package koodies.exec

import koodies.collections.synchronizedSetOf
import koodies.debug.asEmoji
import koodies.exception.toCompactString
import koodies.exec.Exec.Companion.createDump
import koodies.exec.Exec.Companion.fallbackExitStateHandler
import koodies.exec.IO.Meta.File
import koodies.exec.IO.Meta.Starting
import koodies.exec.Process.ExitState
import koodies.exec.Process.ExitState.ExitStateHandler
import koodies.exec.Process.State
import koodies.exec.Process.State.Excepted
import koodies.exec.Process.State.Running
import koodies.io.TeeInputStream
import koodies.io.TeeOutputStream
import koodies.jvm.addShutDownHook
import koodies.jvm.removeShutdownHook
import koodies.text.ANSI.ansiRemoved
import koodies.text.LineSeparators.LF
import koodies.text.Semantics.Symbols.Computation
import koodies.text.Semantics.formattedAs
import koodies.text.TruncationStrategy.MIDDLE
import koodies.text.truncate
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.concurrent.thread
import koodies.io.RedirectingOutputStream as ReOutputStream
import java.lang.Process as JavaProcess

/**
 * Java-based [Exec] implementation.
 */
public class JavaExec(

    private val javaProcess: JavaProcess,

    /**
     * The working directory to be used during execution.
     */
    public override val workingDirectory: Path?,

    /**
     * The command and its arguments to execute.
     */
    public override val commandLine: CommandLine,

    /**
     * If set, the creation of the [ExitState] is delegated to it.
     */
    private val exitStateHandler: ExitStateHandler? = null,

    /**
     * Called the moment the [Exec] terminates—no matter if the [Exec] succeeds or fails
     */
    protected val execTerminationCallback: ExecTerminationCallback? = {},

    /**
     * Whether to kill this [Exec] if it's still running during VM shutdown.
     */
    private val destroyOnShutdown: Boolean = true,
) : Exec {
    public companion object;

    override val pid: Long by lazy { javaProcess.pid() }
    override fun waitFor(): ExitState = (_state as? ExitState) ?: onExit.join()
    override fun stop(): Exec = also { javaProcess.destroy() }
    override fun kill(): Exec = also { javaProcess.destroyForcibly() }

    private var _state: State = Running(pid)

    // onExit needs to be triggered at some point so it updates the state
    // consequently callbacks registered after that moment will likely not be triggered
    override val state: State
        get() {
            onExit
            return _state
        }

    private val capturingInputStream: OutputStream by lazy { TeeOutputStream(javaProcess.outputStream, ReOutputStream { ioLog.input + it }) }
    private val capturingOutputStream: InputStream by lazy { TeeInputStream(javaProcess.inputStream, ReOutputStream { ioLog.output + it }) }
    private val capturingErrorStream: InputStream by lazy { TeeInputStream(javaProcess.errorStream, ReOutputStream { ioLog.error + it }) }

    override val metaStream: MetaStream = MetaStream({ ioLog + it })
    override val inputStream: OutputStream get() = capturingInputStream
    override val outputStream: InputStream get() = capturingOutputStream
    override val errorStream: InputStream get() = capturingErrorStream

    private val ioLog by lazy { IOLog() }
    override val io: IOSequence<IO> get() = IOSequence(ioLog)

    private val preTerminationCallbacks = synchronizedSetOf<Exec.() -> Unit>()
    override fun addPreTerminationCallback(callback: Exec.() -> Unit): Exec =
        apply { preTerminationCallbacks.add(callback) }

    private val postTerminationCallbacks = synchronizedSetOf<Exec.(ExitState) -> Unit>()
    public override fun addPostTerminationCallback(callback: Exec.(ExitState) -> Unit): Exec =
        apply { postTerminationCallbacks.add(callback) }

    private val cachedOnExit: CompletableFuture<out ExitState> by lazy<CompletableFuture<out ExitState>> {
        CompletableFuture.supplyAsync {
            preTerminationCallbacks.mapNotNull { callback ->
                runCatching { this.callback() }.exceptionOrNull()
            }.firstOrNull()?.let { throw it }
        }.thenCombine(javaProcess.onExit()) { _, _ ->
        }.handle { _, throwable ->
            ioLog.flush()
            val exitValue = javaProcess.exitValue()

            val exitState: ExitState = if (throwable != null) {
                val cause: Throwable = (throwable as? CompletionException)?.cause ?: throwable
                val dump = createDump("Process ${commandLine.summary} terminated with ${cause.toCompactString()}.")
                Excepted(pid, exitValue, io, cause, dump.ansiRemoved)
            } else {
                kotlin.runCatching {
                    with(exitStateHandler ?: fallbackExitStateHandler()) { handle(pid, exitValue, io) }
                }.getOrElse { exception ->
                    val formattedPid = pid.formattedAs.input
                    val formattedExitCode = exitValue.formattedAs.input
                    val formattedException = exception.message.formattedAs.error
                    val message = "Unexpected error terminating process $formattedPid with exit code $formattedExitCode:$LF\t$formattedException"
                    Excepted(pid, exitValue, io, exception, createDump(message), message)
                }
            }
            _state = exitState
            postTerminationCallbacks.mapNotNull { callback ->
                runCatching { this.callback(exitState) }.exceptionOrNull()
            }.firstOrNull()?.printStackTrace()
            exitState
        }
    }

    override val onExit: CompletableFuture<out ExitState> get() = (_state as? ExitState)?.let { CompletableFuture.completedFuture(it) } ?: cachedOnExit

    override fun toString(): String {
        val delegateString = "${javaProcess.toString().replaceFirst('[', '(').dropLast(1) + ")"}, successful=${successful?.asEmoji ?: Computation}"
        return "${this::class.simpleName ?: "object"}(delegate=$delegateString)".substringBeforeLast(")") +
            ", commandLine=${commandLine.shellCommand.toCompactString().truncate(50, MIDDLE, " … ")}" +
            ", execTerminationCallback=${(execTerminationCallback != null).asEmoji}" +
            ", destroyOnShutdown=${destroyOnShutdown.asEmoji})"
    }

    init {
        try {
            metaStream.emit(Starting(commandLine))
            commandLine.includedFiles.forEach { metaStream.emit(File(it)) }

            if (destroyOnShutdown) {
                val shutdownHook = thread(start = false, name = "shutdown hook for $pid", contextClassLoader = null) { javaProcess.destroy() }
                addShutDownHook(shutdownHook)

                javaProcess.onExit().handle { _, _ -> removeShutdownHook(shutdownHook) }
            }

            execTerminationCallback?.let { callback ->
                javaProcess.onExit().handle { _, ex -> runCatching { callback.invoke(ex) } }
            }
        } catch (ex: Throwable) {
            kotlin.runCatching { execTerminationCallback?.invoke(ex) }
        }
    }
}

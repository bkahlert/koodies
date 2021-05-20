package koodies.exec

import koodies.Exceptions.ISE
import koodies.exception.toCompactString
import koodies.exec.Process.ExitState
import koodies.exec.Process.State.Excepted
import koodies.exec.Process.State.Exited
import koodies.exec.Process.State.Running
import koodies.logging.ReturnValue
import koodies.text.LineSeparators
import koodies.text.LineSeparators.CRLF
import koodies.text.LineSeparators.withoutTrailingLineSeparator
import koodies.text.Semantics.formattedAs
import koodies.text.takeUnlessBlank
import koodies.text.withSuffix
import koodies.time.Now
import koodies.time.seconds
import koodies.unit.milli
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.time.Duration

/**
 * Platform independent representation of a running program.
 */
public interface Process : ReturnValue {

    /**
     * Stream of this program's meta logs.
     */
    public val metaStream: MetaStream

    /**
     * This program's input stream, that is,
     * the stream you can write to in order
     * to provide input for this program.
     *
     * *In contrast to the extremely misleading naming used in the Java world,
     * this property is named after its purpose—not its type.*
     */
    public val inputStream: OutputStream

    /**
     * This program's output stream, that is,
     * the stream you can read in order to find
     * out what [IO] this process produces.
     *
     * *In contrast to the extremely misleading naming used in the Java world,
     * this property is named after its purpose—not its type.*
     */
    public val outputStream: InputStream

    /**
     * This program's error stream, that is,
     * the stream you can read in order to find
     * out what goes wrong.
     */
    public val errorStream: InputStream

    /**
     * The identifier of this process.
     */
    public val pid: Long

    /**
     * Representation of the state of a [Process].
     *
     * The types are distinguished (which may have more differentiated specializations):
     * - [Running] for already started and not yet terminated processes
     * - [Exited] for processes that terminated with an [exitCode]
     * - [Excepted] for processes that did not run properly
     */
    public sealed interface State : ReturnValue {

        /**
         * PID of the process.
         */
        public val pid: Long

        /**
         * Textual representation of this state.
         */
        public val status: String

        /**
         * Whether this state represents a successful or failed state.
         *
         * Can be `null` if currently unknown (i.e. while running).
         */
        public override val successful: Boolean?

        /**
         * State of a process that already started but not terminated, yet.
         */
        public class Running(
            override val pid: Long,
            override val status: String = "Process $pid is running.",
        ) : State {
            override val successful: Boolean? = null
            override val textRepresentation: String? = "async computation"
            override fun toString(): String = status
        }

        /**
         * State of a process that terminated with an [exitCode].
         */
        public sealed class Exited(

            /**
             * PID of the terminated process at the time is was still running.
             */
            override val pid: Long,

            /**
             * Code the terminated process terminated with.
             */
            public val exitCode: Int,

            /**
             * All [IO] that was logged while the process was running.
             */
            public val io: IOSequence<IO>,

            ) : State {

            /**
             * State of a process that [Exited] successfully.
             */
            public open class Succeeded(
                pid: Long,
                io: IOSequence<IO>,
                override val status: String = "Process ${pid.formattedAs.input} terminated successfully at $Now.",
            ) : ExitState, Exited(pid, 0, io) {
                override val successful: Boolean = true
                override fun toString(): String = status
            }

            /**
             * State of a process that [Exited] erroneously.
             */
            public open class Failed(
                pid: Long,
                exitCode: Int,
                io: IOSequence<IO> = IOSequence.EMPTY,

                private val relevantFiles: List<URI> = emptyList(),
                /**
                 * Detailed information about the circumstances of a process's failed termination.
                 */
                public val dump: String? = null,
                override val status: String = "Process ${pid.formattedAs.input} terminated with exit code ${exitCode.formattedAs.error}.",
            ) : ExitState, Exited(pid, exitCode, io) {
                override val successful: Boolean = false
                override val textRepresentation: String? get() = toString()
                override fun toString(): String =
                    StringBuilder(status).apply {
                        relevantFiles.forEach {
                            append(LineSeparators.LF)
                            append(it)
                        }
                        dump?.takeUnlessBlank()?.let {
                            append(LineSeparators.LF)
                            append(dump)
                        }
                    }.toString()
            }
        }

        /**
         * State of a process that did not run properly.
         */
        public class Excepted(

            /**
             * PID of the terminated process at the time is was still running.
             */
            public override val pid: Long,

            /**
             * Code the terminated process terminated with.
             */
            public override val exitCode: Int,

            /**
             * All [IO] that was logged so far.
             */
            public override val io: IOSequence<IO>,

            /**
             * Unexpected exception that lead to a process's termination.
             */
            public val exception: Throwable?,

            /**
             * Detailed information about the circumstances of a process's unexpected termination.
             */
            public val dump: String,

            override val status: String = "Process excepted with ${exception.toCompactString()}",
        ) : State, ExitState {
            override val successful: Boolean = false
            override val textRepresentation: String = status
            override fun toString(): String = status
        }
    }

    /**
     * The current state of this process.
     */
    public val state: State

    /**
     * Representation of the exit state of a [State.Exited] [Process].
     *
     * The types are distinguished (which may have more differentiated specializations):
     * - [Running] for already started and not yet terminated processes
     * - [Exited] for started and no more running processes
     */
    public interface ExitState : State {

        public override val pid: Long
        public val exitCode: Int
        public val io: IOSequence<IO>

        /**
         * Implementors are used to delegate the creation of the [ExitState] to.
         */
        public fun interface ExitStateHandler {

            /**
             * Returns the [ExitState] of a [Process] based on the
             * given [exited] [State].
             */
            public fun Exec.handle(pid: Long, exitCode: Int, io: IOSequence<IO>): ExitState
        }
    }

    /**
     * Whether the process terminated successfully or failed.
     *
     * `null` is the process has not terminated, yet.
     */
    override val successful: Boolean? get() = state.successful
    override val symbol: String get() = state.symbol
    override val textRepresentation: String? get() = state.textRepresentation

    /**
     * A completable future that returns an instances of this process once
     * the program represented by this process terminated.
     */
    public val onExit: CompletableFuture<out ExitState>

    /**
     * Blocking method that waits until the program represented by this process
     * terminates and returns its [exitValue].
     */
    public fun waitFor(): ExitState = onExit.join()

    /**
     * Returns the exit code of the program represented by process process once
     * it terminates. If the program has not terminated yet, it throws an
     * [IllegalStateException].
     */
    @Deprecated("use exitCode", ReplaceWith("exitCode", "koodies.exec.exitCode")) public val exitValue: Int get() = exitCode

    /**
     * Gracefully attempts to stop the execution of the program represented by this process.
     */
    public fun stop(): Process

    /**
     * Forcefully stops the execution of the program represented by this process.
     */
    public fun kill(): Process
}

/**
 * Returns whether `this` [Process] [isRunning].
 */
public val Process.alive: Boolean get() = state is Running

/**
 * Returns whether `this` [Process] is [Running].
 */
public val Process.isRunning: Boolean get() = state is Running

/**
 * Returns `this` process's [ExitState.exitCode] if it terminated
 * or `null` otherwise.
 */
public val Process.exitCodeOrNull: Int? get() = (state as? Exited)?.exitCode

/**
 * Returns `this` process's [Exited.exitCode].
 *
 * Throws an [IllegalStateException] if the process has not [Exited].
 */
public val Process.exitCode: Int get() = (state as? Exited)?.exitCode ?: throw ISE("Process $pid has not terminated.")

/**
 * Writes the given [input] strings with a slight delay between
 * each input on the [Process]'s [InputStream].
 */
public fun Process.enter(vararg input: String, delay: Duration = 10.milli.seconds): Unit =
    inputStream.enter(*input, delay = delay)

/**
 * Writes the given [input] strings with a slight delay between
 * each input on the [Process]'s [InputStream].
 */
public fun Process.input(vararg input: String, delay: Duration = 10.milli.seconds): Unit =
    inputStream.enter(*input, delay = delay)

/**
 * Writes the given [input] strings with a slight delay between
 * each input on the [Process]'s [InputStream].
 */
public fun OutputStream.enter(vararg input: String, delay: Duration = 10.milli.seconds) {
    val stdin = BufferedWriter(OutputStreamWriter(this))
    input.forEach {
        TimeUnit.MILLISECONDS.sleep(delay.inWholeMilliseconds)
        stdin.write(it.withoutTrailingLineSeparator.withSuffix(CRLF))
        stdin.flush()
    }
}

// PROCESS BUILDER EXTENSIONS

/**
 * Whether built [Process] instances redirect their standard error to standard output.
 */
public var ProcessBuilder.redirectErrorStream: Boolean
    get() = redirectErrorStream()
    set(value) {
        redirectErrorStream(value)
    }

/**
 * This process builder's environment;
 * used by built [Process] instances as their environment.
 */
public val ProcessBuilder.environment: MutableMap<String, String>
    get() = environment()

/**
 * This process builder's working directory;
 * used by built [Process] instances as their working directory.
 *
 * If not working directory is set, the working directory of the current
 * Java process is used (usually the directory named by the system property `user.dir`).
 */
public var ProcessBuilder.workingDirectory: Path?
    get() = directory()?.toPath()
    set(value) {
        value?.toAbsolutePath()?.run {
            require(exists()) { "Working directory $this does not exist." }
            require(isDirectory()) { "Working directory $this is no directory." }
            directory(toFile())
        }
    }

package koodies.logging

import koodies.asString
import koodies.collections.synchronizedMapOf
import koodies.collections.synchronizedSetOf
import koodies.jvm.currentStackTrace
import koodies.runtime.onExit
import koodies.text.ANSI.Formatter
import koodies.text.ANSI.Formatter.Companion.invoke
import koodies.text.LineSeparators.LF
import koodies.text.LineSeparators.hasTrailingLineSeparator
import koodies.text.LineSeparators.mapLines
import koodies.text.LineSeparators.prefixLinesWith
import koodies.text.Semantics.Symbols
import koodies.text.Semantics.formattedAs
import koodies.tracing.OpenTelemetrySpan
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// TODO make interface
// TODO replace log with parent
// TODO make logging only with use/withspan function
// TODO delete ⏳️
// TODO separate tracing and logging/rendering

/**
 * Logger interface to implement loggers that don't just log
 * but render log messages to provide easier understandable feedback.
 */
public open class RenderingLogger(
    public val caption: String,
    protected val parent: RenderingLogger?,
    log: ((String) -> Unit)? = null,
) {

    public open val span: OpenTelemetrySpan = OpenTelemetrySpan(caption, parent?.span)

    protected open var initialized: Boolean = false

    /**
     * Contains whether this logger is open, that is,
     * at least one logging call was received but no result, yet.
     */
    public open val open: Boolean get() = isOpen(this)

    public fun <T> close(result: Result<T>) {
        setOpen(this, false)
        span.end(result)
    }

    /**
     * Contains whether this logger is closed, that is,
     * the logging span was finished with a logged result.
     */
    public val closed: Boolean
        get() = initialized && !open

    /**
     * Lock used to
     * - render logger thread-safe
     * - recognise first logging call
     */
    private val logLock by lazy {
        val lock = ReentrantLock()
        setOpen(this, true)
        initialized = true
        lock
    }

    protected val log: (String) -> Unit by lazy { log ?: { print(it) } }
    protected fun logWithLock(message: () -> String): Unit = logLock.withLock { log(message()) }

    /**
     * Method that is responsible to render the return value of
     * the given [block].
     *
     * If [trailingNewline] is `true` the log message will be appended
     * with a line break.
     *
     * All default implemented methods use this method.
     */
    public open fun render(trailingNewline: Boolean, block: () -> CharSequence): Unit =
        logWithLock {
            if (closed) {
                val prefix = Symbols.Computation + " "
                val message = block().prefixLinesWith(prefix = prefix, ignoreTrailingSeparator = false)
                if (trailingNewline || !message.hasTrailingLineSeparator) message + LF else message
            } else {
                val message = block().toString()
                if (trailingNewline) message + LF else message
            }
        }

    /**
     * Logs raw text.
     *
     * *Please note that in contrast to the other logging methods, **no line separator is added.**.*
     */
    public open fun logText(block: () -> CharSequence): Unit =
        block().let { output ->
            render(false) { output }
        }

    /**
     * Logs a line of text.
     */
    public open fun logLine(block: () -> CharSequence): Unit =
        block().let { output ->
            render(true) { output }
        }

    /**
     * Logs the result of the process this logger is used for.
     */
    public open fun <R> logResult(result: Result<R>): R {
        val formattedResult = ReturnValue.format(result)
        render(true) { formattedResult }
        close(result)
        return result.getOrThrow()
    }

    override fun toString(): String = asString {
        ::open to open
        ::caption to caption
    }

    /**
     * Helper method than can be applied on [CharSequence] returning lambdas
     * to format them using the provided [f] and passing them to [transform]
     * only in case the result was not blank.
     */
    protected fun <T> (() -> CharSequence).format(f: Formatter?, transform: String.() -> T?): T? {
        return f(this()).takeUnless { it.isBlank() }?.toString()?.transform()
    }

    public companion object {

        /**
         * Helper method than can be applied on a list of elements returning the
         * rendered statuses and passing them to [transform]
         * only in case the result was not blank.
         */
        @JvmStatic
        protected fun <T : CharSequence, R> List<T>.format(f: Formatter?, transform: String.() -> R?): R? {
            if (isEmpty()) return null
            return f(asStatus()).takeUnless { it.isBlank() }?.toString()?.transform()
        }

        private fun Array<StackTraceElement>?.asString() = (this ?: emptyArray()).joinToString("") { "$LF\t\tat $it" }

        private val openLoggers: MutableMap<RenderingLogger, Array<StackTraceElement>> = synchronizedMapOf()

        /**
         * Sets the [open] state of the given [logger].
         *
         * Loggers that are not closed the moment this program shuts down
         * are considered broken and will inflict a warning.
         *
         * This behaviour can be disabled using [disabledUnclosedWarningLoggers].
         */
        private fun setOpen(logger: RenderingLogger, open: Boolean) {
            if (open) {
                openLoggers.getOrPut(logger) { currentStackTrace }
            } else {
                openLoggers.remove(logger)
            }
        }

        /**
         * Returns whether this [logger] is unclosed.
         */
        private fun isOpen(logger: RenderingLogger): Boolean = logger in openLoggers

        private val disabledUnclosedWarningLoggers: MutableSet<RenderingLogger> = synchronizedSetOf()

        /**
         * Disables the warning during program exit
         * that shows up if this logger's span was not closed.
         */
        public val <T : RenderingLogger> T.withUnclosedWarningDisabled: T
            get() = also { disabledUnclosedWarningLoggers.add(it) }

        init {
            onExit {
                val unclosed = openLoggers.filterKeys { logger -> logger !in disabledUnclosedWarningLoggers }

                if (unclosed.isNotEmpty()) {
                    println("${unclosed.size} started but unfinished renderer(s) found:".formattedAs.warning)
                    unclosed.forEach { (unclosedLogger: RenderingLogger, stackTrace: Array<StackTraceElement>) ->
                        println()
                        println(unclosedLogger.toString().mapLines { line -> "\t$line" })
                        println("\tcreated:")
                        println(stackTrace.asString())
                    }
                }
            }
        }
    }
}

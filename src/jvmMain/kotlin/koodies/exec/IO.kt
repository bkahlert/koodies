package koodies.exec

import koodies.exec.IO.Companion.ERASE_MARKER
import koodies.exec.IO.Error
import koodies.exec.IO.Input
import koodies.exec.IO.Meta
import koodies.exec.IO.Output
import koodies.text.ANSI.Style
import koodies.text.ANSI.Text.Companion.ansi
import koodies.text.ANSI.ansiRemoved
import koodies.text.AnsiString
import koodies.text.LineSeparators
import koodies.text.LineSeparators.lines
import koodies.text.LineSeparators.mapLines
import koodies.text.Semantics.formattedAs
import koodies.tracing.Event
import koodies.tracing.KoodiesAttributes
import koodies.tracing.KoodiesSpans
import koodies.tracing.RenderingAttributes

/**
 * Instances are ANSI formatted output with a certain type.
 */
public sealed class IO(
    private val type: String,
    /**
     * Contains the originally encountered [IO].
     */
    public val text: AnsiString,
    /**
     * Formats a strings to like an output of this type.
     */
    private val formatAnsi: (AnsiString) -> String,
) : AnsiString(*text.tokens), Event {

    /**
     * Contains this [text] with the format of this type applied.
     */
    @Deprecated("just use text")
    public val formatted: String by lazy { formatAnsi(text) }

    override val name: CharSequence = KoodiesSpans.IO
    override val attributes: Map<CharSequence, Any>
        get() = mapOf(
            KoodiesAttributes.IO_TYPE.key to type,
            KoodiesAttributes.IO_TEXT.key to text.ansiRemoved,
            RenderingAttributes.description(text),
        )

    override fun toString(): String = formatted

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as IO

        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + text.hashCode()
        return result
    }

    /**
     * An [IO] that represents information about a [Process].
     */
    public sealed class Meta(type: String, text: String) : IO(type, text.asAnsiString(), { text.formattedAs.meta }) {

        /**
         * Not further specified information about a [Process].
         */
        public class Text(text: String) : Meta("meta.text", text)

        /**
         * Information about a created [Process] dump.
         */
        public class Dump(dump: String) :
            Meta("meta.dump", dump.also { require(it.contains("dump")) { "Please use ${Text::class.simpleName} for free-form text." } })

        public companion object {
            public infix fun typed(text: CharSequence): Text =
                filter(text).toString().takeIf { it.isNotBlank() }?.let { Text(it) } ?: error("Non-blank string required.")
        }
    }

    /**
     * An [IO] (of another process) serving as an input.
     */
    public class Input(text: AnsiString) : IO("input", text, { text.mapLines { it.ansi.brightBlue.dim.italic.done } }) {
        public companion object {
            private val EMPTY: Input = Input(AnsiString.EMPTY)

            /**
             * Factory to classify different types of [IO].
             */
            public infix fun typed(text: CharSequence): Input = if (text.isEmpty()) EMPTY else Input(filter(text).asAnsiString())
        }

        private val lines: List<Input> by lazy { text.lines().map { Input typed it }.toList() }

        /**
         * Splits this [IO] into separate lines while keeping the ANSI formatting intact.
         */
        public fun lines(): List<Input> = lines
    }

    /**
     * An [IO] that is neither [Meta], [Input] nor [Error].
     */
    public class Output(text: AnsiString) : IO("output", text, { text.mapLines { it.ansi.yellow } }) {
        public companion object {
            private val EMPTY: Output = Output(AnsiString.EMPTY)

            /**
             * Factory to classify different types of [IO].
             */
            public infix fun typed(text: CharSequence): Output = if (text.isEmpty()) EMPTY else Output(filter(text).asAnsiString())
        }

        private val lines by lazy { text.lines().map { Output typed it }.toList() }

        /**
         * Splits this [IO] into separate lines while keeping the ANSI formatting intact.
         */
        public fun lines(): List<IO> = lines
    }

    /**
     * An [IO] that represents an error.
     */
    public class Error(text: AnsiString) : IO("error", text, { text.mapLines { it.ansi.red.bold } }) {

        /**
         * Creates a new error IO from the given [exception].
         */
        public constructor(exception: Throwable) : this(exception.stackTraceToString().asAnsiString())

        public companion object {
            private val EMPTY: Error = Error(AnsiString.EMPTY)

            /**
             * Factory to classify different types of [IO].
             */
            public infix fun typed(text: CharSequence): Error = if (text.isEmpty()) EMPTY else Error(filter(text).asAnsiString())
        }
    }

    public companion object {

        /**
         * Marker that instructs consumers to ignore the remaining line.
         */
        public val ERASE_MARKER: String = Style.hidden.invoke("﹗").toString()

        /**
         * Filters text that starts with [ERASE_MARKER].
         */
        private fun filter(text: CharSequence): CharSequence {
            fun filterText(text: CharSequence) = text.lines().mapNotNull { line ->
                line.takeUnless<CharSequence> { it.startsWith(ERASE_MARKER) }
            }.joinToString(LineSeparators.LF)
            return filterText(text)
        }
    }
}

/** Contains the [IO.Meta] of this [Exec] with [ansiRemoved]. */
public val Exec.meta: String get() = io.meta.ansiRemoved

/** Contains the [IO.Input] of this [Exec] with [ansiRemoved]. */
public val Exec.input: String get() = io.input.ansiRemoved

/** Contains the [IO.Output] of this [Exec] with [ansiRemoved]. */
public val Exec.output: String get() = io.output.ansiRemoved

/** Contains the [IO.Error] of this [Exec] with [ansiRemoved]. */
public val Exec.error: String get() = io.error.ansiRemoved

/**
 * Read optimized [Sequence] of [IO] that can be used
 * as a lazily populating sequence of [IO] or
 * as a means to access all encompassed [IO] merged to
 * a string—either with [ansiRemoved] or [ansiKept].
 */
public class IOSequence<out T : IO>(seq: Sequence<T>) : Sequence<T> by seq {
    public constructor(io: Iterable<T>) : this(io.asSequence())
    public constructor(vararg io: T) : this(io.asSequence())

    /**
     * Contains all encompassed [IO] merged to a string.
     *
     * Eventually existing [ANSI escape codes](https://en.wikipedia.org/wiki/ANSI_escape_code) are removed.
     *
     * ***Note:** Accessing this property triggers the construction
     * of a string representing all encompassed [IO].*
     *
     * @see ansiKept
     */
    public val ansiRemoved: String by lazy { merge<IO>(removeAnsi = true) }

    /**
     * Contains all encompassed [IO] merged to a string.
     *
     * Eventually existing [ANSI escape codes](https://en.wikipedia.org/wiki/ANSI_escape_code) are kept.
     *
     * ***Note:** Accessing this property triggers the construction
     * of a string representing all encompassed [IO].*
     *
     * @see ansiRemoved
     */
    public val ansiKept: String by lazy { merge<IO>(removeAnsi = false) }

    /**
     * Returns all encompassed [IO] merged to a string.
     *
     * Eventually existing [ANSI escape codes](https://en.wikipedia.org/wiki/ANSI_escape_code) are removed.
     *
     * ***Note:** This operation is potentially expensive as triggers the construction
     * of a string representing all encompassed [IO].*
     *
     * @see ansiRemoved
     * @see ansiKept
     */
    override fun toString(): String = ansiRemoved

    /**
     * Returns all encompassed [IO] merged to a string.
     *
     * Whether eventually existing [ANSI escape codes](https://en.wikipedia.org/wiki/ANSI_escape_code) are removed
     * can be specified with [removeAnsi].
     *
     * ***Note:** This operation is potentially expensive as triggers the construction
     * of a string representing all encompassed [IO].*
     *
     * @see ansiRemoved
     * @see ansiKept
     */
    public fun toString(removeAnsi: Boolean): String = if (removeAnsi) ansiRemoved else ansiKept

    public companion object {

        /**
         * An empty [IOSequence].
         */
        public val EMPTY: IOSequence<IO> = IOSequence(emptySequence())
    }
}

/**
 * Filters this [IO] sequence by the specified type.
 *
 * By default [ANSI escape codes](https://en.wikipedia.org/wiki/ANSI_escape_code) are removed.
 * Set [removeAnsi] to `false` to keep escapes codes.
 */
public inline fun <reified T : IO> Sequence<IO>.merge(removeAnsi: Boolean = true): String =
    filterIsInstance<T>().joinToString(LineSeparators.LF) { if (removeAnsi) it.unformatted else it.formatted }

/**
 * Contains a filtered copy only consisting of [Meta].
 */
public val Sequence<IO>.meta: IOSequence<Meta> get() = IOSequence(filterIsInstance<Meta>())

/**
 * Contains a filtered copy only consisting of [Input].
 */
public val Sequence<IO>.input: IOSequence<Input> get() = IOSequence(filterIsInstance<Input>())

/**
 * Contains a filtered copy only consisting of [Output].
 */
public val Sequence<IO>.output: IOSequence<Output> get() = IOSequence(filterIsInstance<Output>())

/**
 * Contains a filtered copy only consisting of [Error].
 */
public val Sequence<IO>.error: IOSequence<Error> get() = IOSequence(filterIsInstance<Error>())

/**
 * Contains a filtered copy only consisting of [Output] and [Error].
 */
public val Sequence<IO>.outputAndError: IOSequence<IO> get() = IOSequence(filter { it is Output || it is Error })

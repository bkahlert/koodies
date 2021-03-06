package koodies.tracing.rendering

import io.opentelemetry.api.trace.Span
import koodies.exception.toCompactString
import koodies.text.LineSeparators.LF
import koodies.text.Semantics.FieldDelimiters
import koodies.text.Semantics.Symbolizable
import koodies.text.Semantics.Symbols
import koodies.text.takeUnlessBlank
import koodies.tracing.CurrentSpan

/**
 * Implementors of this interface gain control on
 * how it is displayed as the result of a [CurrentSpan].
 */
public interface ReturnValue : Symbolizable {

    /**
     * Whether this return value represents a successful state.
     */
    public val successful: Boolean

    override val symbol: String get() = if (successful) Symbols.OK else Symbols.Error

    /**
     * Text representing this return value.
     */
    public val textRepresentation: String? get() = null

    /**
     * Formats this return value by placing the symbol left to the text.
     */
    public fun format(): String = textRepresentation?.takeUnlessBlank()?.let { "$symbol $it" } ?: symbol

    public companion object {

        /**
         * Converts any value to a [ReturnValue] used to compute its representation
         * based on whether it implements [ReturnValue] or not.
         *
         * If [ReturnValue] is implemented its implementation is used. Otherwise
         * [ExceptionReturnValue] is used for instances of [Throwable] and [AnyReturnValue]
         * for any other value.
         */
        public fun of(value: Any?): ReturnValue =
            when (value) {
                is ReturnValue -> value
                is Result<*> -> value.fold({ of(it) }, { of(it) })
                is Throwable -> ExceptionReturnValue(value)
                else -> AnyReturnValue(value)
            }

        public fun <T> successful(value: T, transform: T.() -> String? = { null }): ReturnValue =
            object : ReturnValue {
                override val successful: Boolean = true
                override val textRepresentation: String?
                    get() = value.transform()
            }

        /**
         * Computes the representation of the given [returnValue] as a result of a [Span].
         *
         * If [ReturnValue] is implemented its implementation is used. Otherwise
         * [ExceptionReturnValue] is used for instances of [Throwable] and [AnyReturnValue]
         * for any other value.
         */
        public fun format(returnValue: Any?): String = of(returnValue).format()
    }
}

/**
 * Mutable list of return values that is considered [successful] if does not contain any
 * failed [ReturnValue].
 */
public class ReturnValues<E>(vararg elements: E) : MutableList<E> by mutableListOf(*elements), ReturnValue {
    private val unsuccessful: List<ReturnValue> get() = map { ReturnValue.of(it) }.filter { !it.successful }
    override val successful: Boolean
        get() = fold(true) { acc: Boolean, el: E ->
            if (ReturnValue.of(el).successful) acc else false
        }

    override val textRepresentation: String?
        get() = when (unsuccessful.size) {
            0 -> null
            1 -> unsuccessful.single().textRepresentation
            else -> "Multiple problems encountered: " + unsuccessful.joinToString("") { returnValue ->
                (returnValue.textRepresentation ?: "").lines()
                    .mapNotNull { line -> line.takeUnlessBlank() }
                    .joinToString(prefix = "$LF    ${returnValue.symbol} ", separator = " ${FieldDelimiters.FIELD} ")
            }
        }

    /**
     * Adds the elements of the given [returnValues] to this list.
     */
    public operator fun plus(returnValues: ReturnValues<out E>): ReturnValues<E> = apply { addAll(returnValues) }
}

/**
 * Default [ReturnValue] implementation for the given [value].
 *
 * If [value] is a [ReturnValue] itself this implementation delegates to it.
 *
 * Otherwise [value] is considered [successful] and is formatted using [toCompactString].
 */
@JvmInline
public value class AnyReturnValue(private val value: Any?) : ReturnValue {
    override val successful: Boolean
        get() = if (value is ReturnValue) {
            value.successful
        } else {
            true
        }

    override val symbol: String
        get() = when (value) {
            is ReturnValue -> value.symbol
            Unit -> Symbols.OK
            null -> Symbols.Null
            else -> super.symbol
        }

    override val textRepresentation: String?
        get() = when {
            value is ReturnValue -> value.textRepresentation
            !successful -> value?.toCompactString()
            else -> super.textRepresentation
        }
}


/**
 * Default [ReturnValue] implementation for the given [exception].
 *
 * The given [exception] is considered non-[successful] and is formatted using [toCompactString].
 */
@JvmInline
public value class ExceptionReturnValue(private val exception: Throwable) : ReturnValue {
    override val successful: Boolean get() = false
    override val textRepresentation: String? get() = if (!successful) exception.toCompactString() else null
}

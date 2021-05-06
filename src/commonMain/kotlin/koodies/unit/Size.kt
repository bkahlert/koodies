package koodies.unit

import koodies.math.BigDecimal
import koodies.math.BigDecimalConstants
import koodies.math.BigInteger
import koodies.math.BigIntegerConstants
import koodies.math.absoluteValue
import koodies.math.dec
import koodies.math.div
import koodies.math.isZero
import koodies.math.minus
import koodies.math.plus
import koodies.math.shl
import koodies.math.times
import koodies.math.toAtMostDecimalsString
import koodies.math.toBigDecimal
import koodies.math.toBigInteger
import koodies.math.toScientificString
import koodies.math.toString
import koodies.math.unaryMinus
import koodies.text.Semantics.formattedAs
import kotlin.jvm.JvmInline
import kotlin.reflect.KClass

/**
 * Amount of bytes representable in with the decimal [SI prefixes](https://en.wikipedia.org/wiki/Metric_prefix)
 * [Yotta], [Zetta], [Exa], [Peta], [Tera], [Giga], [Mega], [kilo] as well as with the binary prefixes as defined by
 * [ISO/IEC 80000](https://en.wikipedia.org/wiki/ISO/IEC_80000) [Yobi], [Zebi], [Exbi], [Pebi], [Tebi], [Gibi], [Mebi] and [Kibi].
 */
@JvmInline
public value class Size(public val bytes: BigDecimal) : Comparable<Size> {

    public val bits: BigInteger get() = bytes.toBigInteger() * Byte.SIZE_BITS

    /**
     * Computes the amount of characters needed at most to represent
     * this amount of [bytes] to the specified [base].
     *
     * E.g. `2.bytes.maxLengthOfRepresentationToBaseOf(8) == 4`
     * which is the max length of two bytes represented in octal notation.
     */
    public fun maxLengthOfRepresentationToBaseOf(base: Int): Int = (BigIntegerConstants.TWO shl bits.dec().toInt()).dec().toString(base).length

    public companion object {
        public val ZERO: Size = Size(BigDecimalConstants.ZERO)
        public val supportedPrefixes: Map<KClass<out UnitPrefix>, List<UnitPrefix?>> = mapOf(
            BinaryPrefix::class to listOf(
                BinaryPrefix.Yobi,
                BinaryPrefix.Zebi,
                BinaryPrefix.Exbi,
                BinaryPrefix.Pebi,
                BinaryPrefix.Tebi,
                BinaryPrefix.Gibi,
                BinaryPrefix.Mebi,
                BinaryPrefix.Kibi,
                null,
                BinaryPrefix.mibi,
                BinaryPrefix.mubi,
                BinaryPrefix.nabi,
                BinaryPrefix.pibi,
                BinaryPrefix.fembi,
                BinaryPrefix.abi,
                BinaryPrefix.zebi,
                BinaryPrefix.yobi,
            ),
            DecimalPrefix::class to listOf(
                DecimalPrefix.Yotta,
                DecimalPrefix.Zetta,
                DecimalPrefix.Exa,
                DecimalPrefix.Peta,
                DecimalPrefix.Tera,
                DecimalPrefix.Giga,
                DecimalPrefix.Mega,
                DecimalPrefix.kilo,
//                DecimalPrefix.hecto,
//                DecimalPrefix.deca,
                null,
//                DecimalPrefix.deci,
//                DecimalPrefix.centi,
                DecimalPrefix.milli,
                DecimalPrefix.micro,
                DecimalPrefix.nano,
                DecimalPrefix.pico,
                DecimalPrefix.femto,
                DecimalPrefix.atto,
                DecimalPrefix.zepto,
                DecimalPrefix.yocto,
            )
        )
        public const val SYMBOL: String = "B"

        public fun precision(value: BigDecimal, unit: UnitPrefix?): Int = when (unit) {
            null -> 0
            else -> when {
                value <= BigDecimalConstants.ONE -> 3
                value <= BigDecimalConstants.TEN -> 2
                value <= BigDecimalConstants.HUNDRED -> 1
                else -> 0
            }
        }
    }

    /**
     * Returns a string representation of this size value expressed in the unit
     * with the [UnitPrefix] which yields the most compact and readable number value.
     *
     * Special cases:
     *  - zero size is formatted as `"0 B"`
     *  - fraction sizes are formatted as `"0 B"`
     *  - very big sizes (more than a million [Yotta]byte/[Yobi]byte) are expressed
     *  in [Yotta]byte/[Yobi]byte and formatted in scientific notation
     *
     * @return the value of size in the automatically determined [UnitPrefix], e.g. 42.2 MB.
     */
    override fun toString(): String = toString<DecimalPrefix>()

    /**
     * Returns a string representation of this size value expressed in the unit
     * with the [UnitPrefix] which yields the most compact and readable number value.
     *
     * Special cases:
     *  - zero size is formatted as `"0 B"`
     *  - fraction sizes are formatted as `"0 B"`
     *  - very big sizes (more than a million [Yotta]byte/[Yobi]byte) are expressed
     *  in [Yotta]byte/[Yobi]byte and formatted in scientific notation
     *
     * @return the value of size in the automatically determined [UnitPrefix], e.g. 42.2 MB.
     */
    public inline fun <reified T : UnitPrefix> toString(prefixType: KClass<T> = T::class, decimals: Int? = null): String {
        if (bytes == BigDecimalConstants.ZERO) return "0 $SYMBOL"

        val prefixes: List<UnitPrefix?>? = supportedPrefixes[prefixType]
        require(prefixes != null) { "$prefixType is not supported. Valid options are: " + supportedPrefixes.keys }

        val absoluteValue = bytes.absoluteValue
        val prefixesGreaterThanOrEqualRequestedPrefix = prefixes.dropLastWhile { absoluteValue >= it.factor }.size
        val scientific = run {
            val millionish = prefixes.find { it != null }
                ?.let { unitPrefix -> unitPrefix.baseFactor * unitPrefix.baseFactor }
                ?: error("At least one supported unit prefix required.")
            prefixesGreaterThanOrEqualRequestedPrefix == 0 && absoluteValue >= prefixes.first().factor.times(millionish, 0)
        }
        val prefix = prefixes.getOrNull(prefixesGreaterThanOrEqualRequestedPrefix)
        val value = bytes.div(prefix.factor, 128)
        val formattedValue = when {
            scientific -> value.toScientificString()
            else -> value.toAtMostDecimalsString(decimals ?: precision(value.absoluteValue, prefix))
        }
        return "$formattedValue ${prefix.getSymbol<Size>()}$SYMBOL"
    }

    /**
     * Returns a string representation of this size value expressed with the given [unitPrefix]
     * and formatted with the specified [decimals] number of digits after decimal point.
     *
     * @return the value of duration in the specified [unitPrefix]
     *
     * @throws IllegalArgumentException if [decimals] is less than zero.
     */
    public fun toString(unitPrefix: UnitPrefix, decimals: Int = 0): String {
        require(decimals >= 0) { "decimals must be not negative, but was $decimals" }
        val value: BigDecimal = bytes / unitPrefix.factor
        val upperDetailLimit = 1e14.toBigDecimal()
        return when {
            value.absoluteValue < upperDetailLimit -> value.toAtMostDecimalsString(decimals.coerceAtMost(12))
            else -> value.toScientificString()
        } + " " + unitPrefix.getSymbol<Size>() + SYMBOL
    }

    override fun compareTo(other: Size): Int = this.bytes.compareTo(other.bytes)

    public operator fun unaryPlus(): Size = this
    public operator fun unaryMinus(): Size = this * -BigDecimalConstants.ONE

    public operator fun plus(other: BigDecimal): Size = Size(bytes + other)
    public operator fun plus(other: Number): Size = this + other.toDouble().toBigDecimal()
    public operator fun plus(other: Size): Size = this + other.bytes

    public operator fun minus(other: BigDecimal): Size = Size(bytes - other)
    public operator fun minus(other: Number): Size = this - other.toDouble().toBigDecimal()
    public operator fun minus(other: Size): Size = this - other.bytes

    public operator fun times(factor: BigDecimal): Size = (factor * bytes).bytes
    public operator fun times(factor: Number): Size = this * factor.toDouble().toBigDecimal()

    public operator fun div(other: BigDecimal): Size = Size(bytes / other)
    public operator fun div(other: Number): Size = this / other.toDouble().toBigDecimal()
    public operator fun div(other: Size): BigDecimal = bytes / other.bytes
}

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the sequence.
 *
 * The operation is _terminal_.
 */
public inline fun <T> Sequence<T>.sumBy(selector: (T) -> Size): Size {
    var sum: Size = Size.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

/**
 * Tries to parse this char sequence as a [Size] instance (e.g. `1 MiB` or `1.32GB`).
 *
 * @see parse
 */
public fun CharSequence.toSize(): Size = parse()


/**
 * Tries to parse this char sequence as a [Size] instance (e.g. `1 MiB` or `1.32GB`).
 *
 * Sizes with and without decimals, as much as all binary and decimal units
 * either with or without a space between value and unit are supported.
 */
public fun CharSequence.parse(): Size {
    val (value, unit) = UnitPrefix.parse(this)
    require(unit?.equals(Size.SYMBOL) != false) { "${unit.formattedAs.input} is no valid size unit like MB or GiB." }
    return value.bytes
}

/**
 * Contains the equivalent value as [bytes].
 */
public val Number.bytes: Size
    get() = if (isZero) Size.ZERO else Size(toDouble().toBigDecimal())

/**
 * Contains the equivalent value as [bytes].
 */
public val BigDecimal.bytes: Size
    get() = if (isZero) Size.ZERO else Size(this)

/**
 * Contains the equivalent value as [bytes].
 */
public val BigInteger.bytes: Size
    get() = if (isZero) Size.ZERO else Size(toString(10).toBigDecimal(10))

/**
 * Contains the equivalent value as [bytes].
 */
public val Number.bits: Size
    get() = if (isZero) Size.ZERO else toDouble().toBigDecimal().bits

/**
 * Contains the equivalent value as [bytes].
 */
public val BigDecimal.bits: Size
    get() = if (isZero) Size.ZERO else Size(this / Byte.SIZE_BITS.toBigDecimal())

/**
 * Contains the equivalent value as [bytes].
 */
public val BigInteger.bits: Size
    get() = if (isZero) Size.ZERO else toBigDecimal().bits

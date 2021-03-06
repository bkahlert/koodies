package koodies.net

import koodies.math.BigInteger
import koodies.math.BigIntegerConstants
import koodies.math.bigIntegerOf
import koodies.math.padStart
import koodies.math.shl
import koodies.math.toString
import koodies.math.toUByteArray
import koodies.math.trim
import koodies.net.Notation.Verbosity
import koodies.net.Notation.Verbosity.Compressed
import koodies.net.Notation.Verbosity.Conventional
import koodies.net.Notation.Verbosity.Full
import koodies.ranges.size
import koodies.regex.countMatches
import koodies.unit.bytes
import kotlin.math.ceil

/**
 * Encapsulates the knowledge on how to format Internet Protocol addresses.
 *
 * All permutations of the offered dimensions are possible, that is, not only
 * - `192.168.16.1` (see [conventionalRepresentation]) or
 * - `::ffff:c0a8:1001` (see [compressedRepresentation]) but also
 * - `⌁⌁60⌁ag401` (IPv4 address `192.168.16.1` mapped to made up addresses
 * of 24 bytes in 8 groups of quadlets formatted to the base of 32 and
 * the longest consecutive groups of zeros replaced by `⌁⌁`, see [format]).
 */
public interface Notation {

    /**
     * Level of detail of a Internet Protocol address representation.
     */
    public enum class Verbosity {
        /**
         * Fully detailed representation
         */
        Full,

        /**
         * Typical representation where obviously redundant
         * information like leading zeros are left out.
         */
        Conventional,

        /**
         * Representation that applies the compression rule
         * of replacing the longest sequence of groups with value `0`
         * by two consecutive group separators.
         */
        Compressed
    }

    /**
     * Specifies how many bytes one address has (e.g. `16` bytes = `128` Bit for IPv6).
     */
    public val byteCount: Int

    /**
     * Specifies how many bytes make up one group (IPv4: `1`, IPv6: `2`).
     */
    public val groupSize: Int

    /**
     * Specifies how groups are separated.
     */
    public val groupSeparator: Char

    /**
     * Specifies the base of the representation respectively how many states a single digit can have.
     */
    public val base: Int

    /**
     * Specifies which [Verbosity] to use by default.
     */
    public val defaultVerbosity: Verbosity

    /**
     * Formats the Internet Protocol address specified by its [value] using
     * this notations [byteCount], [groupSize], [groupSeparator], [base] and [defaultVerbosity].
     */
    public fun format(value: BigInteger): String = format(value, defaultVerbosity)

    /**
     * Formats the Internet Protocol address specified by its [value] using
     * this notations [byteCount], [groupSize], [groupSeparator], [base] and
     * the specified [defaultVerbosity].
     */
    public fun format(value: BigInteger, verbosity: Verbosity): String {
        require(byteCount > 0) { "Byte count must be positive." }
        require(value.compareTo(BigIntegerConstants.ZERO) >= 0 && value <= BigIntegerConstants.TWO shl (byteCount * Byte.SIZE_BITS)) { "$value exceeds 2^${byteCount * Byte.SIZE_BITS}." }
        val conventional = value.toUByteArray().trim() // minimal bytes
            .padStart(ceil(byteCount.div(groupSize.toDouble())).times(groupSize).toInt()) // all bytes
            .windowed(groupSize, groupSize) // groups
            .map { bigIntegerOf(it.toUByteArray()) }
            .map { it.toString(base) } // base

        return when (verbosity) {
            Full -> {
                val length = groupSize.bytes.maxLengthOfRepresentationToBaseOf(base)
                conventional.map { byte -> byte.padStart(length, '0') }.joinToString(groupSeparator.toString())
            }
            Compressed -> {
                val string = conventional.joinToString(groupSeparator.toString())
                Regex.fromLiteral("$groupSeparator$groupSeparator").countMatches(string).takeIf { it == 0 }?.let {
                    val pattern = Regex("(?:^|$groupSeparator)(0+(?:${groupSeparator}0+)+)")
                    pattern.findAll(string)
                        .maxByOrNull { it.value.length }?.run {
                            if (range.size == string.length) "$groupSeparator$groupSeparator"
                            else {
                                if (this.value.startsWith(groupSeparator)) {
                                    string.replaceRange((range.first + 1)..range.last, groupSeparator.toString())
                                } else {
                                    string.replaceRange(range, groupSeparator.toString())
                                }
                            }
                        }
                } ?: string
            }
            Conventional -> {
                conventional.joinToString(groupSeparator.toString())
            }
        }
    }
}

public object IPv4Notation : Notation {
    override val byteCount: Int = IPv4Address.byteCount
    override val groupSize: Int = 1
    override val groupSeparator: Char = '.'
    override val base: Int = 10
    override val defaultVerbosity: Verbosity = Conventional
}

/**
 * This representation consists of four octets each consisting of
 * one to three decimal digits—leading zeros removed.
 *
 * Example: `192.168.0.1`
 */
public val IPv4Address.conventionalRepresentation: String get() = IPv4Notation.format(value)


public object IPv6Notation : Notation {
    override val byteCount: Int = IPv6Address.byteCount
    override val groupSize: Int = 2
    override val groupSeparator: Char = ':'
    override val base: Int = 16
    override val defaultVerbosity: Verbosity = Compressed
}

/**
 * This representation consists of eight hextets each consisting of four
 * hexadecimal digits—leading zeros included.
 *
 * Example: `0000:0000:0000:0000:0000:ffff:c0a8:1001`
 *
 * @see conventionalRepresentation
 * @see compressedRepresentation
 * @see <a href="https://tools.ietf.org/html/rfc5952">A Recommendation for IPv6 Address Text Representation</a>
 */
public val IPv6Address.fullRepresentation: String get() = IPv6Notation.format(value, Full)

/**
 * This representation consists of eight hextets each consisting of
 * one to four hexadecimal digits—leading zeros removed.
 *
 * Example: `0:0:0:0:0:ffff:c0a8:1001`
 *
 * @see fullRepresentation
 * @see compressedRepresentation
 * @see <a href="https://tools.ietf.org/html/rfc5952">A Recommendation for IPv6 Address Text Representation</a>
 */
public val IPv6Address.conventionalRepresentation: String get() = IPv6Notation.format(value, Conventional)

/**
 * This representation consists of up to eight hextets each consisting of
 * one to four hexadecimal digits—leading zeros removed.
 *
 * This is the shortest representation as it removes the longest sequence
 * of `0` hextets—given such a sequences spans at least two hextets.
 *
 * Example: `::ffff:c0a8:1001`
 *
 * @see fullRepresentation
 * @see conventionalRepresentation
 * @see <a href="https://tools.ietf.org/html/rfc5952">A Recommendation for IPv6 Address Text Representation</a>
 */
public val IPv6Address.compressedRepresentation: String get() = IPv6Notation.format(value, Compressed)

package koodies.math

/**
 * Immutable arbitrary-precision integers.
 */
public actual class BigInteger : Number(), Comparable<BigInteger> {
    override fun toByte(): Byte {
        TODO("Not yet implemented")
    }

    override fun toChar(): Char {
        TODO("Not yet implemented")
    }

    override fun toDouble(): Double {
        TODO("Not yet implemented")
    }

    override fun toFloat(): Float {
        TODO("Not yet implemented")
    }

    override fun toInt(): Int {
        TODO("Not yet implemented")
    }

    override fun toLong(): Long {
        TODO("Not yet implemented")
    }

    override fun toShort(): Short {
        TODO("Not yet implemented")
    }

    override fun compareTo(other: BigInteger): Int {
        TODO("Not yet implemented")
    }
}

/**
 * Enables the use of the `+` operator for [BigInteger] instances.
 */
public actual operator fun BigInteger.plus(other: BigInteger): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Enables the use of the `-` operator for [BigInteger] instances.
 */
public actual operator fun BigInteger.minus(other: BigInteger): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Enables the use of the `*` operator for [BigInteger] instances.
 */
public actual operator fun BigInteger.times(other: Int): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Enables the use of the `*` operator for [BigInteger] instances.
 */
public actual operator fun BigInteger.times(other: BigInteger): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Enables the use of the unary `-` operator for [BigInteger] instances.
 */
public actual operator fun BigInteger.unaryMinus(): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Enables the use of the `++` operator for [BigInteger] instances.
 */
public actual operator fun BigInteger.inc(): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Enables the use of the `--` operator for [BigInteger] instances.
 */
public actual operator fun BigInteger.dec(): BigInteger {
    TODO("Not yet implemented")
}

/** Inverts the bits including the sign bit in this value. */
public actual inline val BigInteger.invertedValue: BigInteger
    get() {
        TODO("Not yet implemented")
    }

/** Performs a bitwise AND operation between the two values. */
public actual infix fun BigInteger.and(other: BigInteger): BigInteger {
    TODO("Not yet implemented")
}

/** Performs a bitwise OR operation between the two values. */
public actual infix fun BigInteger.or(other: BigInteger): BigInteger {
    TODO("Not yet implemented")
}

/** Performs a bitwise XOR operation between the two values. */
public actual infix fun BigInteger.xor(other: BigInteger): BigInteger {
    TODO("Not yet implemented")
}

/** Shifts this value left by the [n] number of bits. */
public actual infix fun BigInteger.shl(n: Int): BigInteger {
    TODO("Not yet implemented")
}

/** Shifts this value right by the [n] number of bits, filling the leftmost bits with copies of the sign bit. */
public actual infix fun BigInteger.shr(n: Int): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Returns the value of this [Int] number as a [BigInteger].
 */
public actual fun Int.toBigInteger(): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Returns the value of this [UInt] number as a [BigInteger].
 */
public actual fun UInt.toBigInteger(): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Returns the value of this [BigDecimal] number as a [BigInteger].
 */
public actual fun BigDecimal.toBigInteger(): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Returns the value of this [CharSequence] representing a number
 * to the given [radix] as a [BigInteger].
 */
public actual fun CharSequence.toBigInteger(radix: Int): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Returns the value of this [BigInteger] as a [ByteArray].
 */
public actual fun BigInteger.toByteArray(): ByteArray {
    TODO("Not yet implemented")
}

/**
 * Returns the value of this [BigInteger] as a [UByteArray].
 */
public actual fun BigInteger.toUByteArray(): UByteArray {
    TODO("Not yet implemented")
}

/**
 * Creates a [BigInteger] from this [ByteArray].
 */
public actual fun ByteArray.toBigInteger(): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Creates a [BigInteger] from this [UByteArray].
 */
public actual fun UByteArray.toBigInteger(): BigInteger {
    TODO("Not yet implemented")
}

/**
 * Returns a string representation of this [BigInteger] value in the specified [radix].
 */
public actual fun BigInteger.toString(radix: Int): String {
    TODO("Not yet implemented")
}

public actual object BigIntegerConstants {
    /**
     * The BigInteger constant zero.
     */
    public actual val ZERO: BigInteger
        get() = TODO("Not yet implemented")

    /**
     * The BigInteger constant one.
     */
    public actual val ONE: BigInteger
        get() = TODO("Not yet implemented")

    /**
     * The BigInteger constant two.
     */
    public actual val TWO: BigInteger
        get() = TODO("Not yet implemented")

    /**
     * The BigInteger constant ten.
     */
    public actual val TEN: BigInteger
        get() = TODO("Not yet implemented")

    /**
     * The BigDecimal constant ten.
     */
    public actual val HUNDRED: BigInteger
        get() = TODO("Not yet implemented")
}

/**
 * Returns the absolute value of this value.
 */
public actual val BigInteger.absoluteValue: BigInteger
    get() = TODO("Not yet implemented")

/**
 * Raises this value to the power [n].
 */
public actual fun BigInteger.pow(n: Int): BigInteger = TODO("Not yet implemented")

/**
 * Returns a number having a single bit set in the position of the most significant set bit of this [BigInteger] number,
 * or zero, if this number is zero.
 */
public actual fun BigInteger.takeHighestOneBit(): Int {
    TODO("Not yet implemented")
}

/**
 * Returns a number having a single bit set in the position of the least significant set bit of this [BigInteger] number,
 * or zero, if this number is zero.
 */
public actual fun BigInteger.takeLowestOneBit(): Int {
    TODO("Not yet implemented")
}

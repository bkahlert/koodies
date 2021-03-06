package koodies

import koodies.Either.Left
import koodies.Either.Right
import koodies.debug.xray
import koodies.math.BigInteger
import koodies.math.BigIntegerConstants
import koodies.math.toBigInteger
import koodies.runtime.isDebugging
import koodies.text.ANSI.containsAnsi
import koodies.text.Semantics.formattedAs
import koodies.text.truncate
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract

/**
 * Runs the specified [block] if `this` is `null` and provide
 * and expected instance of [T].
 *
 * Example:
 * ```kotlin
 * val value: String? = …
 *  …
 * val result: String = value otherwise { "fallback" }
 * ```
 *
 * @see takeIf
 */
public inline infix fun <T> T?.otherwise(block: () -> T): T {
    contract {
        callsInPlace(block, EXACTLY_ONCE)
    }
    return this ?: block()
}

/**
 * Wraps the specified function [block] by calling the specified functions [before] and [after]
 * the actual invocation.
 *
 * Then return value of [before] is passed as an argument to [after] which
 * is handy if a value needs to be changed and restored.
 *
 * Returns the invocations result on success and throws the encountered exception on failure.
 * In both cases [after] will be called.
 */
public fun <U, R> runWrapping(before: () -> U, after: (U) -> Unit, block: (U) -> R): R {
    val u = before()
    val r = runCatching { block(u) }
    after(u)
    return r.getOrThrow()
}

/**
 * Wraps the specified function [block] with `this` value as its receiver by calling the specified functions [before] and [after]
 * the actual invocation.
 *
 * Then return value of [before] is passed as an argument to [after] which
 * is handy if a value needs to be changed and restored.
 *
 * Returns the invocations result on success and throws the encountered exception on failure.
 * In both cases [after] will be called.
 */
public fun <T, U, R> T.runWrapping(before: T.() -> U, after: T.(U) -> Unit, block: T.(U) -> R): R {
    val u = before()
    val r = runCatching { block(u) }
    after(u)
    return r.getOrThrow()
}

/**
 * Runs the provided [block] `this` times with an increasing index passed
 * on each call and returns a list of the returned results.
 */
public operator fun <R> Int.times(block: (index: Int) -> R): List<R> {
    require(this >= 0) { "times must not be negative" }
    return when (this) {
        0 -> emptyList()
        else -> (0 until this).map(block)
    }
}

/**
 * Runs the provided [block] `this` times with an increasing index passed
 * on each call and returns a list of the returned results.
 */
public operator fun <R> BigInteger.times(block: (index: Int) -> R): List<R> {
    require(this >= BigIntegerConstants.ZERO) { "times must not be negative" }
    require(this <= Int.MAX_VALUE.toBigInteger()) { "times must not be greater than ${Int.MAX_VALUE}" }
    return toInt().times(block)
}

/**
 * Invokes this nullable identity lambda with [arg] and returns its result.
 * If this is `null`, [arg] is returned unchanged.
 */
public operator fun <R, T : (R) -> R> T?.invoke(arg: R): R = this?.invoke(arg) ?: arg


// @formatter:off
/** Throws an [IllegalArgumentException] if `this` string [isEmpty]. */
public fun String.requireNotEmpty(): String = also{require(it.isNotEmpty())}
/** Throws an [IllegalArgumentException] with the result of calling [lazyMessage] if `this` string [isEmpty]. */
public fun String.requireNotEmpty(lazyMessage: () -> Any): String = also{require(it.isNotEmpty(),lazyMessage)}
/** Throws an [IllegalArgumentException] if `this` string [isBlank]. */
public fun String.requireNotBlank(): String = also{require(isNotBlank())}
/** Throws an [IllegalArgumentException] with the result of calling [lazyMessage] if `this` string [isBlank]. */
public fun String.requireNotBlank(lazyMessage: () -> Any): String = also{require(it.isNotBlank(),lazyMessage)}

/** Throws an [IllegalStateException] if `this` string [isEmpty]. */
public fun String.checkNotEmpty(): String = also{check(it.isNotEmpty())}
/** Throws an [IllegalStateException] with the result of calling [lazyMessage] if `this` string [isEmpty]. */
public fun String.checkNotEmpty(lazyMessage: () -> Any): String = also{check(it.isNotEmpty(),lazyMessage)}
/** Throws an [IllegalStateException] if `this` string [isBlank]. */
public fun String.checkNotBlank(): String = also{check(it.isNotBlank())}
/** Throws an [IllegalStateException] with the result of calling [lazyMessage] if `this` string [isBlank]. */
public fun String.checkNotBlank(lazyMessage: () -> Any): String = also{check(it.isNotBlank(),lazyMessage)}
// @formatter:on

/**
 * Throws an [IllegalArgumentException] with the details if the value is obviously
 * spoiled with unusual characters such as escape sequences.
 */
public fun String.requireSaneInput() {
    require(!containsAnsi) {
        "ANSI escape sequences detected: $xray"
    }
    if (length > 1) {
        mapOf(
            "double quotes" to '\"',
            "single quotes" to '\'',
        ).forEach { (name, char) ->
            require((first() == char) == (last() == char)) {
                val annotatedInput = truncate(30).let {
                    val error = "$char".formattedAs.input
                    if ((first() == char)) error + it.substring(1, lastIndex)
                    else it.substring(0, lastIndex - 1) + error
                }

                "Unmatched $name detected: $annotatedInput"
            }
        }
    }
}

/**
 * Returns `this` object if this program runs in debug mode or `null`, if it's not.
 */
public fun <T> T.takeIfDebugging(): T? = takeIf { isDebugging }

/**
 * Returns `this` object if this program does not run in debug mode or `null`, if it is.
 */
public fun <T> T.takeUnlessDebugging(): T? = takeIf { isDebugging }

/**
 * Represents a container containing either an instance of type [A] ([Left])
 * or [B] ([Right]).
 */
public sealed interface Either<A, B> {
    public class Left<A, B>(public val left: A) : Either<A, B>
    public class Right<A, B>(public val right: B) : Either<A, B>
}

/**
 * Returns either the encapsulated instance [A] or
 * the encapsulated instance [B] transformed to an instance of [A]
 * using the given [transform].
 */
public inline infix fun <reified A, reified B> Either<A, B>.or(transform: (B) -> A): A =
    when (this) {
        is Left -> left
        is Right -> transform(right)
    }

/**
 * Returns either the encapsulated instance [A] mapped using [transform] or
 * the encapsulated instance [B].
 */
public inline fun <reified A, reified B, reified R> Either<A, B>.map(transform: A.() -> R): Either<R, B> =
    when (this) {
        is Left -> Left(left.transform())
        is Right ->
            @Suppress("UNCHECKED_CAST")
            this.also { check(it === right) } as Either<R, B>
    }

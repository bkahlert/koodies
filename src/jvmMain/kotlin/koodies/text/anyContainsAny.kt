package koodies.text

/**
 * Returns `true` if any of the character sequences contains any of the specified [others] as a substring.
 *
 * @param ignoreCase `true` to ignore character case when comparing strings. By default `false`.
 */
public fun <T : CharSequence, U : CharSequence> Iterable<T>.anyContainsAny(others: Iterable<U>, ignoreCase: Boolean = false): Boolean =
    any { it.containsAny(others, ignoreCase = ignoreCase) }

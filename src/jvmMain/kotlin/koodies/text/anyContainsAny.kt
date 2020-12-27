package koodies.text

/**
 * Returns `true` if any of the char sequences contains any of the specified [others] as a substring.
 *
 * @param ignoreCase `true` to ignore character case when comparing strings. By default `false`.
 */
fun <T : CharSequence, U : CharSequence> Iterable<T>.anyContainsAny(others: Iterable<U>, ignoreCase: Boolean = false): Boolean =
    any { it.containsAny(others, ignoreCase = ignoreCase) }
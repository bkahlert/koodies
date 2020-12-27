package koodies.text

/**
 * Returns `true` if this char sequence contains all of the specified [others] as a substring.
 *
 * @param ignoreCase `true` to ignore character case when comparing strings. By default `false`.
 */
fun <T : CharSequence> CharSequence.containsAll(others: Iterable<T>, ignoreCase: Boolean = false): Boolean =
    others.all { contains(it, ignoreCase = ignoreCase) }
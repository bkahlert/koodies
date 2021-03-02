package koodies.collections

/**
 * Creates a tuple of type [Triple] from `this` [Pair] and [that].
 *
 * @sample tripleFromTo
 */
fun <A, B, C> Pair<A, B>.to(that: C) =
    Triple(first, second, that)

private fun tripleFromTo() {
    "first" to "second" to "third"
}

package koodies.io.file

import koodies.io.path.pathString
import koodies.test.testEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.Assertion
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isFailure
import java.nio.file.Path

class ResolveSiblingKtTest {

    @Test
    fun `should resolve sibling path`() {
        expectThat(Path.of("/a/b/c").resolveSibling { resolveSibling(fileName.pathString + "-x") }).pathStringIsEqualTo("/a/b-x/c")
    }

    @Test
    fun `should resolve with returned multi-segment path`() {
        expectThat(Path.of("/a/b/c.d").resolveSibling { resolveSibling("1/e") }).pathStringIsEqualTo("/a/1/e/c.d")
    }

    @TestFactory
    fun `should apply order`() = listOf(
        0 to "/a/b/c-x",
        1 to "/a/b-x/c",
        2 to "/a-x/b/c",
    ).testEach { (order, expected) ->
        expecting { Path.of("/a/b/c").resolveSibling(order) { resolveSibling(fileName.pathString + "-x") } } that { pathStringIsEqualTo(expected) }
    }

    @Test
    fun `should throw on more levels requested than present`() {
        expectCatching { Path.of("/a/b/c").resolveSibling(order = 3) { this } }.isFailure().isA<IllegalArgumentException>()
    }

    @Test
    fun `should throw on negative order`() {
        expectCatching { Path.of("/a/b/c").resolveSibling(order = -1) { this } }.isFailure().isA<IllegalArgumentException>()
    }
}

fun <T : Path> Assertion.Builder<T>.isSiblingOf(expected: Path, order: Int = 1) =
    assert("is sibling of order $order") { actual ->
        val actualNames = actual.map { name -> name.pathString }.toList()
        val otherNames = expected.map { name -> name.pathString }.toList()
        val actualIndex = actualNames.size - order - 1
        val otherIndex = otherNames.size - order - 1
        val missing = (actualIndex - otherNames.size + 1)
        if (missing > 0) {
            fail("$expected is too short. At least $missing segments are missing to be able to be sibling.")
        }
        if (missing <= 0) {
            val evaluation = actualNames.zip(otherNames).mapIndexed { index, namePair ->
                val match = if (index == actualIndex || index == otherIndex) true
                else namePair.first == namePair.second
                namePair to match
            }
            val matches = evaluation.takeWhile { (_, match) -> match }.map { (namePair, _) -> namePair.first }
            val misMatch = evaluation.getOrNull(matches.size)?.let { (namePair, _) -> namePair }
            if (misMatch != null) fail("Paths match up to $matches, then mismatch $misMatch")
            else pass()
        }
    }

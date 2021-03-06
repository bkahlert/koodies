package koodies.io.file

import koodies.test.testEach
import org.junit.jupiter.api.TestFactory
import strikt.api.Assertion
import strikt.assertions.isEqualTo
import java.nio.file.Path

class QuotedKtTest {

    @TestFactory
    fun `should quote path`() = listOf(
        "filename" to "\"filename\"",
        "filename.test" to "\"filename.test\"",
        "my/path/filename" to "\"my/path/filename\"",
        "my/path/filename.test" to "\"my/path/filename.test\"",
        "/my/path/filename" to "\"/my/path/filename\"",
        "/my/path/filename.test" to "\"/my/path/filename.test\"",
    ).testEach("{} -> {}") { (path, expected) ->
        expecting { Path.of(path) } that { quoted.isEqualTo(expected) }
    }
}

val <T : Path> Assertion.Builder<T>.quoted
    get() = get("wrapped in quotes %s") { quoted }

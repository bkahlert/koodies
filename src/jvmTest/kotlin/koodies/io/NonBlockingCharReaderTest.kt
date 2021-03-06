package koodies.io

import koodies.exec.mock.SlowInputStream.Companion.slowInputStream
import koodies.nio.NonBlockingCharReader
import koodies.number.times
import koodies.test.Slow
import koodies.text.ANSI
import koodies.text.ANSI.Text.Companion.ansi
import koodies.text.styling.Borders
import koodies.text.styling.wrapWithBorder
import koodies.time.seconds
import koodies.unit.milli
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
import org.opentest4j.AssertionFailedError
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThanOrEqualTo
import java.io.InputStreamReader

@Execution(CONCURRENT)
class NonBlockingCharReaderTest {

    @Test
    fun `should read null if empty`() {
        val reader = NonBlockingCharReader("".byteInputStream(), 100.milli.seconds)
        5 times { expectThat(reader.read(CharArray(1), 0)).isLessThanOrEqualTo(0) }
        10 times { expectThat(reader.read(CharArray(1), 0)).isEqualTo(-1) }
    }

    @Test
    fun `should return null if source is closed`() {
        val reader = NonBlockingCharReader("123".byteInputStream(), 100.milli.seconds)
        expectThat(reader.readText()).isEqualTo("123")
        5 times { expectThat(reader.read(CharArray(1), 0)).isLessThanOrEqualTo(0) }
        10 times { expectThat(reader.read(CharArray(1), 0)).isEqualTo(-1) }
    }

    @Slow @Test
    fun `should read content`() {
        val line = "line #壹\nline #❷"
        val reader = NonBlockingCharReader(line.byteInputStream(), 100.milli.seconds)
        expectThat(reader.readLines()).containsExactly("line #壹", "line #❷")
    }

    @Slow @Test
    fun `should read in a non-greedy fashion resp just as much as needed to avoid blocking`() {
        val inputStream = slowInputStream(
            0.seconds,
            1.seconds to "123",
            2.seconds to "abc",
            3.seconds to "!§\"",
        )
        val reader = NonBlockingCharReader(inputStream, 2.seconds)

        kotlin.runCatching {
            expectThat(reader.readLines()).containsExactly("123abc!\"")
                .get { this[0] }.not { contains("$") } // needs a wrapper like NonBlockingReader for characters of length > 1 byte
        }.recover {
            val color = ANSI.Colors.green
            if (it is AssertionFailedError) throw it
            fail(listOf("An exception has occurred while reading the input stream.",
                "Please make sure you don't use a greedy implementation like",
                InputStreamReader::class.qualifiedName?.ansi?.magenta?.toString() + ".",
                "\nTheir reading strategy blocks the execution leaving you with nothing but timeouts and exceptions.",
                color.invoke(org.jline.utils.InputStreamReader::class.qualifiedName.toString()).toString() + "",
                " is known to be a working non-greedy implementation.")
                .wrapWithBorder(Borders.SpikedOutward), it)
        }
    }
}

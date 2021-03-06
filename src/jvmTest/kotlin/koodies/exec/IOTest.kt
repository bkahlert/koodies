package koodies.exec

import koodies.exec.IO.Error
import koodies.exec.IO.Input
import koodies.exec.IO.Meta
import koodies.exec.IO.Meta.Dump
import koodies.exec.IO.Meta.Text
import koodies.exec.IO.Output
import koodies.test.toStringIsEqualTo
import koodies.text.ANSI.Text.Companion.ansi
import koodies.text.LineSeparators.LF
import koodies.text.Unicode.TAB
import koodies.text.containsAnsi
import koodies.text.toStringMatchesCurlyPattern
import koodies.tracing.rendering.RenderingAttributes
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure

class IOTest {

    @Nested
    inner class Meta {

        @Nested
        inner class Text {

            private val text = "text"
            private val meta = Text(text)

            @Test
            fun `should have text`() {
                expectThat(meta.text).toStringIsEqualTo(text)
            }

            @Test
            fun `should throw on blank`() {
                expectCatching { Meta typed "    " }.isFailure()
            }
        }

        @Nested
        inner class Dump {

            private val dump = "dump"
            private val meta = Dump(dump)

            @Test
            fun `should have text`() {
                expectThat(meta.text).toStringIsEqualTo(dump)
            }

            @Test
            fun `should throw on non-dump`() {
                expectCatching { Dump("whatever") }.isFailure()
            }
        }
    }

    @Nested
    inner class Input {

        private val `in` = Input typed "in"

        @Test
        fun `should have text`() {
            expectThat(`in`.text).toStringIsEqualTo("in")
        }
    }

    @Nested
    inner class Output {

        private val out = Output typed "out"

        @Test
        fun `should have text`() {
            expectThat(out.text).toStringIsEqualTo("out")
        }
    }

    @Nested
    inner class Error {

        private val err = Error(RuntimeException("err"))

        @Test
        fun `should have stacktrace`() {
            expectThat(err.text).toStringMatchesCurlyPattern("""
                {}.RuntimeException: err
                ${TAB}at koodies.{}
                {{}}
            """.trimIndent())
        }
    }

    @Nested
    inner class IOProperties {

        @Test
        fun `should filter meta`() {
            expectThat(IO_LIST.meta.toList()).containsExactly(IO_LIST.toList().subList(0, 2))
        }

        @Test
        fun `should filter in`() {
            expectThat(IO_LIST.input.toList()).containsExactly(IO_LIST.toList().subList(2, 3))
        }

        @Test
        fun `should filter out`() {
            expectThat(IO_LIST.output.toList()).containsExactly(IO_LIST.toList().subList(3, 4))
        }

        @Test
        fun `should filter err`() {
            expectThat(IO_LIST.error.toList()).containsExactly(IO_LIST.toList().subList(4, 5))
        }

        @Test
        fun `should filter out and err`() {
            expectThat(IO_LIST.outputAndError.toList()).containsExactly(IO_LIST.toList().subList(3, 5))
        }

        @Test
        fun `should remove ansi escape codes`() {
            expectThat(IO_LIST.ansiRemoved).not { containsAnsi() }
        }

        @Test
        fun `should keep ansi escape codes`() {
            expectThat(IO_LIST.ansiKept).containsAnsi()
        }

        @Test
        fun `should merge multiple types`() {
            expectThat(IO_LIST.take(2).merge<IO>(removeAnsi = true)).isEqualTo("text${LF}dump")
        }

        @Test
        fun `should merge single type`() {
            expectThat(IO_LIST.output.ansiRemoved).isEqualTo("out")
        }
    }

    @Nested
    inner class AsEvent {

        private val io = Output typed "test"

        @Test
        fun `have name`() {
            expectThat(io.name).isEqualTo("koodies.exec.io")
        }

        @Test
        fun `should have type`() {
            expectThat(io.attributes).contains(IOAttributes.TYPE to "output")
        }

        @Test
        fun `should have text`() {
            expectThat(io.attributes).contains(IOAttributes.TEXT to "test")
        }

        @Test
        fun `should have rendering only description`() {
            expectThat(io.attributes).contains(RenderingAttributes.DESCRIPTION renderingOnly io.text)
        }
    }

    companion object {
        val IO_LIST: IOSequence<IO> = IOSequence(
            Text("text"),
            Dump("dump"),
            Input typed "in".ansi.blue,
            Output typed "out".ansi.yellow,
            Error(RuntimeException("err")),
        )
    }
}

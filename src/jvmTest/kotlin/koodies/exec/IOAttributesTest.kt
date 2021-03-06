package koodies.exec

import io.opentelemetry.api.common.Attributes
import koodies.exec.IOAttributes.Companion.io
import koodies.test.test
import koodies.tracing.Key
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.startsWith

class IOAttributesTest {

    private val ioAttributes = Attributes.of(
        IOAttributes.TYPE, "output",
        IOAttributes.TEXT, "/root",
        Key.stringKey("irrelevant-key"), "irrelevant value",
    ).io

    @TestFactory
    fun `should read attributes`() = test(ioAttributes) {
        expecting { type } that { isEqualTo("output") }
        expecting { text } that { isEqualTo("/root") }
    }

    @Nested
    inner class ToString {

        @Test
        fun `should start with class name`() {
            expectThat(ioAttributes.toString())
                .startsWith("IOAttributes")
        }

        @Test
        fun `should contain all attributes`() {
            expectThat(ioAttributes.toString())
                .contains("koodies.exec.io.type = output")
                .contains("koodies.exec.io.text = /root")
                .contains("irrelevant-key = irrelevant value")
        }
    }
}

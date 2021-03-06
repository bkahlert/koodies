package koodies.tracing.rendering

import io.opentelemetry.api.common.Attributes
import koodies.test.test
import koodies.tracing.Key
import koodies.tracing.rendering.RenderingAttributes.Keys.rendering
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.startsWith

class RenderingAttributesTest {

    private val renderingAttributes = Attributes.of(
        RenderingAttributes.DESCRIPTION, "custom description",
        RenderingAttributes.RENDERER, "custom renderer",
        Key.stringKey("irrelevant-key"), "irrelevant value",
    ).rendering

    @TestFactory
    fun `should read attributes`() = test(renderingAttributes) {
        expecting { description } that { isEqualTo("custom description") }
        expecting { renderer } that { isEqualTo("custom renderer") }
    }

    @Nested
    inner class ToString {

        @Test
        fun `should start with class name`() {
            expectThat(renderingAttributes.toString())
                .startsWith("RenderingAttributes")
        }

        @Test
        fun `should contain all attributes`() {
            expectThat(renderingAttributes.toString())
                .contains("description = custom description")
                .contains("koodies.renderer = custom renderer")
                .contains("irrelevant-key = irrelevant value")
        }
    }
}

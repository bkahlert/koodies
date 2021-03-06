package koodies.text

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class CutKtTest {

    @Test
    fun `should return left and right substring each from given pos `() {
        expectThat("Hello World!".cut(3u)).isEqualTo("Hel" to "lo World!")
    }

    @Test
    fun `should return empty left on 0`() {
        expectThat("Hello World!".cut(0u)).isEqualTo("" to "Hello World!")
    }

    @Test
    fun `should return empty left on length or above pos`() {
        expectThat("Hello World!".cut(1000_000u)).isEqualTo("Hello World!" to "")
    }
}

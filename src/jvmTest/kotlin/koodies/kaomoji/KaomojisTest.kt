package koodies.kaomoji

import koodies.kaomoji.Kaomojis.fishing
import koodies.kaomoji.Kaomojis.thinking
import koodies.terminal.AnsiFormats.hidden
import koodies.terminal.IDE
import koodies.test.test
import koodies.test.toStringIsEqualTo
import koodies.text.codePointSequence
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
import strikt.api.expectThat
import strikt.assertions.endsWith
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.startsWith

@Execution(CONCURRENT)
class KaomojisTest {

    @RepeatedTest(10)
    fun `should create random Kaomoji`() {
        val kaomoji = Kaomojis.random()
        expectThat(kaomoji).get { codePointSequence().count() }.isGreaterThanOrEqualTo(3)
    }

    @TestFactory
    fun `should create random Kaomoji from`() =
        Kaomojis.Generator.values().test("{}") { category ->
            val kaomoji = category.random()
            expectThat(kaomoji).get { codePointSequence().count() }.isGreaterThanOrEqualTo(3)
        }

    @RepeatedTest(10)
    fun `should create random dogs`() = (0 until 10).map { i ->
        val kaomoji = Kaomojis.Dogs.random()
        expectThat(kaomoji).get { length }.isGreaterThanOrEqualTo(5)
    }

    @RepeatedTest(10)
    fun `should create random wizards`() = (0 until 10).map { i ->
        val kaomoji = Kaomojis.`(＃￣_￣)o︠・━・・━・━━・━☆`.random()
        expectThat(kaomoji).get { length }.isGreaterThanOrEqualTo(5)
    }

    @Nested
    inner class RandomThinkingKaomoji {
        @Test
        fun `should create thinking Kaomoji`() {
            val hidden = if (IDE.isIntelliJ) "    " else "・㉨・".hidden()
            expectThat(Kaomojis.Bear[0].thinking("oh no")).isEqualTo("""
                $hidden   ͚͔˱ ❨ ( oh no )
                ・㉨・ ˙
            """.trimIndent())
        }
    }

    @Nested
    inner class RandomFishingKaomoji {
        @Test
        fun `should be created with random fisher and specified fish`() {
            expectThat(fishing(Kaomojis.Fish.`❮°«⠶＞˝`)).endsWith("o/￣￣￣❮°«⠶＞˝")
        }

        @Test
        fun `should be created with specified fisher and random fish`() {
            expectThat(Kaomojis.Shrug[0].fishing()).startsWith("┐(´д｀)o/￣￣￣")
        }
    }

    @Nested
    inner class Categories {
        @Test
        fun `should use manually specified form`() {
            val kaomoji = Kaomojis.Angry.`(`A´)`
            expectThat(kaomoji).toStringIsEqualTo("(`A´)")
        }

        @Test
        fun `should parse automatically`() {
            val kaomoji = Kaomojis.Angry.`(`A´)`
            expectThat(kaomoji) {
                get("left arm") { leftArm }.isEqualTo("(")
                get("right arm") { rightArm }.isEqualTo(")")
                get("left eye") { leftEye }.isEqualTo("`")
                get("right eye") { rightEye }.isEqualTo("´")
                get("mouth") { mouth }.isEqualTo("A")
            }
        }

        @Test
        fun `should be enumerable`() {
            expectThat(Kaomojis.Angry.subList(2, 5).joinToString { "$it" })
                .isEqualTo("눈_눈, ಠ⌣ಠ, ಠ▃ಠ")
        }
    }
}
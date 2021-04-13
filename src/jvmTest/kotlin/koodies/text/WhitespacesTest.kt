package koodies.text

import koodies.test.string
import koodies.test.test
import koodies.text.Whitespaces.EM_QUAD
import koodies.text.Whitespaces.EM_SPACE
import koodies.text.Whitespaces.EN_QUAD
import koodies.text.Whitespaces.EN_SPACE
import koodies.text.Whitespaces.FIGURE_SPACE_FO
import koodies.text.Whitespaces.FOUR_PER_EM_SPACE
import koodies.text.Whitespaces.HAIR_SPACE
import koodies.text.Whitespaces.IDEOGRAPHIC_SPACE
import koodies.text.Whitespaces.MEDIUM_MATHEMATICAL_SPACE
import koodies.text.Whitespaces.MONGOLIAN_VOWEL_SEPARATOR
import koodies.text.Whitespaces.NARROW_NO_BREAK_SPACE_FO
import koodies.text.Whitespaces.NO_BREAK_SPACE
import koodies.text.Whitespaces.OGHAM_SPACE_MARK
import koodies.text.Whitespaces.PUNCTUATION_SPACE
import koodies.text.Whitespaces.SIX_PER_EM_SPACE
import koodies.text.Whitespaces.SPACE
import koodies.text.Whitespaces.THIN_SPACE
import koodies.text.Whitespaces.THREE_PER_EM_SPACE
import koodies.text.Whitespaces.ZERO_WIDTH_NO_BREAK_SPACE
import koodies.text.Whitespaces.ZERO_WIDTH_SPACE
import koodies.text.Whitespaces.hasTrailingWhitespaces
import koodies.text.Whitespaces.trailingWhitespaces
import koodies.text.Whitespaces.unify
import koodies.text.Whitespaces.withoutTrailingWhitespaces
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isTrue

@Execution(CONCURRENT)
class WhitespacesTest {

    @Test
    fun `should iterate all whitespaces in order`() {
        expectThat(Whitespaces.joinToString(" ") { "($it)" }).isEqualTo("($SPACE) ($NO_BREAK_SPACE) ($OGHAM_SPACE_MARK) ($MONGOLIAN_VOWEL_SEPARATOR) ($EN_QUAD) ($EM_QUAD) ($EN_SPACE) ($EM_SPACE) ($THREE_PER_EM_SPACE) ($FOUR_PER_EM_SPACE) ($SIX_PER_EM_SPACE) ($FIGURE_SPACE_FO) ($PUNCTUATION_SPACE) ($THIN_SPACE) ($HAIR_SPACE) ($ZERO_WIDTH_SPACE) ($NARROW_NO_BREAK_SPACE_FO) ($MEDIUM_MATHEMATICAL_SPACE) ($IDEOGRAPHIC_SPACE) ($ZERO_WIDTH_NO_BREAK_SPACE)")
    }

    @Nested
    inner class Dict {

        @Test
        fun `should iterate all line break names in order`() {
            expectThat(Whitespaces.Dict.values.joinToString(" ") { "($it)" }).isEqualTo(
                "(SPACE) " +
                    "(NO BREAK SPACE) " +
                    "(OGHAM SPACE MARK) " +
                    "(MONGOLIAN VOWEL SEPARATOR) " +
                    "(EN QUAD) " +
                    "(EM QUAD) " +
                    "(EN SPACE) " +
                    "(EM SPACE) " +
                    "(THREE PER EM SPACE) " +
                    "(FOUR PER EM SPACE) " +
                    "(SIX PER EM SPACE) " +
                    "(FIGURE SPACE FO) " +
                    "(PUNCTUATION SPACE) " +
                    "(THIN SPACE) " +
                    "(HAIR SPACE) " +
                    "(ZERO WIDTH SPACE) " +
                    "(NARROW NO BREAK SPACE FO) " +
                    "(MEDIUM MATHEMATICAL SPACE) " +
                    "(IDEOGRAPHIC SPACE) " +
                    "(ZERO WIDTH NO BREAK SPACE)")
        }

        @Test
        fun `should iterate all line breaks in order`() {
            expectThat(Whitespaces.Dict.keys.joinToString(" ") { "($it)" }).isEqualTo("($SPACE) ($NO_BREAK_SPACE) ($OGHAM_SPACE_MARK) ($MONGOLIAN_VOWEL_SEPARATOR) ($EN_QUAD) ($EM_QUAD) ($EN_SPACE) ($EM_SPACE) ($THREE_PER_EM_SPACE) ($FOUR_PER_EM_SPACE) ($SIX_PER_EM_SPACE) ($FIGURE_SPACE_FO) ($PUNCTUATION_SPACE) ($THIN_SPACE) ($HAIR_SPACE) ($ZERO_WIDTH_SPACE) ($NARROW_NO_BREAK_SPACE_FO) ($MEDIUM_MATHEMATICAL_SPACE) ($IDEOGRAPHIC_SPACE) ($ZERO_WIDTH_NO_BREAK_SPACE)")
        }
    }

    @TestFactory
    fun `each whitespace`() = test {
        Whitespaces.Dict.forEach { (whitespace, name) ->
            group(name) {
                expect("has length") { whitespace.length }.that { isEqualTo(1) }
                expect("equal to itself") { whitespace.asCodePoint() }.that {
                    isNotNull().string.isEqualTo(whitespace)
                }

                expect("trailing if at end") { "line$whitespace".trailingWhitespaces }.that { isEqualTo(whitespace) }
                expect("not be trailing if not at end") { "line${whitespace}X".trailingWhitespaces }.that { isEmpty() }

                expect("trailing group if left") { "line$whitespace ".trailingWhitespaces }.that { isEqualTo(whitespace + SPACE) }
                expect("trailing group if right") { "line $whitespace".trailingWhitespaces }.that { isEqualTo(SPACE + whitespace) }

                expect("true if trailing") { "line$whitespace".hasTrailingWhitespaces }.that { isTrue() }
                expect("false if not trailing") { "line${whitespace}X".hasTrailingWhitespaces }.that { isFalse() }

                expect("be removed if part of trailing whitespaces") { "line$whitespace".withoutTrailingWhitespaces }.that { isEqualTo("line") }
                expect("not be removed if not part") { "line${whitespace}X".withoutTrailingWhitespaces }.that { isEqualTo("line${whitespace}X") }
                expect("be replaced by single space") { unify("abc${whitespace}def") }.that { isEqualTo("abc${SPACE}def") }
            }
        }
    }
}

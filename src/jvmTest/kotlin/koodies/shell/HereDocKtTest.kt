package koodies.shell

import koodies.concurrent.process.io
import koodies.concurrent.process.merged
import koodies.concurrent.process.out
import koodies.concurrent.script
import koodies.test.toStringIsEqualTo
import koodies.text.ANSI.ansiRemoved
import koodies.text.lines
import koodies.text.matchesCurlyPattern
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.assertions.isNotBlank
import strikt.assertions.isNotEqualTo

@Execution(CONCURRENT)
class HereDocKtTest {

    @Test
    fun `should create here document using given delimiter and line separator`() {
        val hereDoc = HereDoc("line 1", "line 2", delimiter = "MY-DELIMITER", lineSeparator = "␤")
        expectThat(hereDoc).toStringIsEqualTo("<<MY-DELIMITER␤line 1␤line 2␤MY-DELIMITER")
    }

    @Test
    fun `should create here document using HERE- delimiter and line feed by default`() {
        val hereDoc = listOf("line 1", "line 2").toHereDoc()
        expectThat(hereDoc).matchesCurlyPattern("""
            <<HERE-{}
            line 1
            line 2
            HERE-{}
        """.trimIndent())
    }

    @Nested
    inner class ParameterSubstitution {

        @Nested
        inner class Enabled {

            private val hereDoc = HereDoc(commands = arrayOf("\$HOME"), delimiter = "TEST")

            @Test
            fun `should not change delimiter`() {
                expectThat(hereDoc).lines().first().isEqualTo("<<TEST")
            }

            @Test
            fun `should substitute parameters`() {
                expectThat(script {
                    !"cat $hereDoc"
                }.io.out.merged.ansiRemoved) {
                    isNotBlank()
                    isNotEqualTo("\$HOME")
                }
            }
        }

        @Nested
        inner class Disabled {

            private val hereDoc = HereDoc(commands = arrayOf("\$HOME"), delimiter = "TEST", substituteParameters = false)

            @Test
            fun `should not single quote delimiter`() {
                expectThat(hereDoc).lines().first().isEqualTo("<<'TEST'")
            }

            @Test
            fun `should not substitute parameters`() {
                expectThat(script {
                    !"cat $hereDoc"
                }.io.out.merged.ansiRemoved) {
                    isEqualTo("\$HOME")
                }
            }
        }
    }

    @Test
    fun `should accept empty list`() {
        val hereDoc = listOf<String>().toHereDoc()
        expectThat(hereDoc).matchesCurlyPattern("""
            <<HERE-{}
            HERE-{}
        """.trimIndent())
    }

    @Nested
    inner class Support {
        @Test
        fun `for Array`() {
            val hereDoc = arrayOf("line 1", "line 2").toHereDoc()
            expectThat(hereDoc).matchesCurlyPattern("""
            <<HERE-{}
            line 1
            line 2
            HERE-{}
        """.trimIndent())
        }

        @Test
        fun `for Iterable`() {
            val hereDoc = listOf("line 1", "line 2").asIterable().toHereDoc()
            expectThat(hereDoc).matchesCurlyPattern("""
            <<HERE-{}
            line 1
            line 2
            HERE-{}
        """.trimIndent())
        }
    }

    @Nested
    inner class VarargConstructor {
        @Test
        fun `should take unnamed arguments as lines `() {
            val hereDoc = HereDoc("line 1", "line 2")
            expectThat(hereDoc).matchesCurlyPattern("""
            <<HERE-{}
            line 1
            line 2
            HERE-{}
        """.trimIndent())
        }


        @Test
        fun `should take named arguments as such`() {
            val hereDoc = HereDoc("line 1", "line 2", label = "test")
            expectThat(hereDoc).matchesCurlyPattern("""
            <<test
            line 1
            line 2
            test
        """.trimIndent())
        }
    }
}

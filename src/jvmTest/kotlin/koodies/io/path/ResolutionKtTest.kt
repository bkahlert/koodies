package koodies.io.path

import koodies.io.file.WrappedPath
import koodies.io.randomFile
import koodies.io.randomPath
import koodies.junit.UniqueId
import koodies.jvm.contextClassLoader
import koodies.test.withTempDir
import koodies.text.Unicode
import koodies.text.randomString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.java.exists
import kotlin.io.path.readText

class ResolutionKtTest {

    @Nested
    inner class ToPath {

        @Test
        fun `should return regular path`(uniqueId: UniqueId) = withTempDir(uniqueId) {
            val path = randomFile("file", ".txt")
            expectThat("$path".asPath())
                .isEqualTo(path)
                .not { isA<WrappedPath>() }
        }

        @Test
        fun `should not check existence`(uniqueId: UniqueId) = withTempDir(uniqueId) {
            val path = randomPath("file", ".txt")
            expectThat("$path".asPath())
                .isEqualTo(path)
                .not { exists() }
        }
    }


    @Nested
    inner class ToMappedPath {

        @Test
        fun `should map regular path`(uniqueId: UniqueId) = withTempDir(uniqueId) {
            val randomContent = randomString()
            val uri = randomFile().writeText(randomContent).toUri()
            val readContent = uri.toMappedPath { it.readText() }
            expectThat(readContent).isEqualTo(randomContent)
        }

        @Test
        fun `should map stdlib class path`() {
            val url = Regex::class.java.getResource("Regex.class")
            val siblingFileNames = url.toMappedPath { it.readText() }
            expectThat(siblingFileNames)
                .contains("Matcher")
                .contains("MatchResult")
                .contains("getRange")
                .contains(" ")
                .contains("")
                .contains(Unicode.startOfHeading.toString())
        }

        @Test
        fun `should map own class path`() {
            val uri = contextClassLoader.getResource("junit-platform.properties")?.toURI()
            val content: String? = uri?.toMappedPath { it.readText() }
            expectThat(content).isNotNull().contains("SimplifiedDisplayNameGenerator")
        }
    }
}

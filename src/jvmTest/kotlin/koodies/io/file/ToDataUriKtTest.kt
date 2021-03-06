package koodies.io.file

import koodies.io.copyToDirectory
import koodies.junit.UniqueId
import koodies.test.HtmlFixture
import koodies.test.withTempDir
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ToDataUriKtTest {

    @Test
    fun `should create data URI`(uniqueId: UniqueId) = withTempDir(uniqueId) {
        val htmlFile = HtmlFixture.copyToDirectory(this)

        @Suppress("SpellCheckingInspection")
        expectThat(htmlFile.toDataUri())
            .isEqualTo("data:text/html;base64,PGh0bWw+CiAgPGhlYWQ+PHRpdGxlPkh" +
                "lbGxvIFRpdGxlITwvdGl0bGU+CjwvaGVhZD4KPGJvZHk+CiAgICA8aDE+SGV" +
                "sbG8gSGVhZGxpbmUhPC9oMT4KICAgIDxwPkhlbGxvIFdvcmxkITwvcD4KPC9" +
                "ib2R5Pgo8L2h0bWw+")
    }
}

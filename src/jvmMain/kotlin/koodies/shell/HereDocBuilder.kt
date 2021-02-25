package koodies.shell

import koodies.builder.Init
import koodies.builder.ListBuilder
import koodies.builder.context.ListBuildingContext
import koodies.text.CharRanges
import koodies.text.LineSeparators
import koodies.text.randomString

object HereDocBuilder {
    /**
     * Returns a random—most likely unique—label to be used for a [HereDoc].
     */
    fun randomLabel(): String = "HERE-" + randomString(8, allowedCharacters = CharRanges.UpperCaseAlphanumeric).toUpperCase()

    /**
     * The line separator used by default to separate lines in a [HereDoc].
     */
    const val DEFAULT_LINE_SEPARATOR: String = LineSeparators.LF

    fun Init<ListBuildingContext<String>>.hereDoc(
        label: String = randomLabel(),
        lineSeparator: String = DEFAULT_LINE_SEPARATOR,
    ) = hereDoc(label = label, lineSeparator = lineSeparator, init = this)

    fun hereDoc(
        label: String = randomLabel(),
        lineSeparator: String = DEFAULT_LINE_SEPARATOR,
        init: Init<ListBuildingContext<String>>,
    ): String = ListBuilder(init).let { lines ->
        mutableListOf("<<$label").apply { addAll(lines) }.apply { add(label) }.joinToString(separator = lineSeparator)
    }
}

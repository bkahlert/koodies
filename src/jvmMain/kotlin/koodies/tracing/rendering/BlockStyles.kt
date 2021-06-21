package koodies.tracing.rendering

import koodies.logging.ReturnValue
import koodies.text.ANSI.Formatter
import koodies.text.ANSI.Text.Companion.ansi
import koodies.text.AnsiString.Companion.asAnsiString
import koodies.text.LineSeparators.lines
import koodies.text.prefixWith
import koodies.text.withPrefix

public interface BlockStyle : Style {
    public val indent: Int
}

public object BlockStyles {

    public object Rounded : BlockStyle {

        // @formatter:off
        private val topLeft =          "╭──╴"
        private val prefix =           "│"
        private val incompletePrefix = "╵"
        private val bottomLeft =       "╰──╴"
        // @formatter:on

        override val indent: Int = content("") { it.toString() }?.length ?: 0

        override fun start(element: CharSequence, decorationFormatter: Formatter): CharSequence? = buildString {
            val startElements = element.asAnsiString().lines()
            appendLine(decorationFormatter(topLeft), decorationFormatter(startElements.first()).ansi.bold)
            startElements.drop(1).forEach { startElement ->
                appendLine(decorationFormatter(prefix), decorationFormatter(startElement).ansi.bold)
            }
            append(decorationFormatter(prefix))
        }

        override fun content(element: CharSequence, decorationFormatter: Formatter): CharSequence? = buildString {
            append(decorationFormatter(prefix), "   ", element)
        }

        override fun end(element: ReturnValue, resultValueFormatter: (ReturnValue) -> ReturnValue?, decorationFormatter: Formatter): CharSequence? {
            val processReturnValue = resultValueFormatter(element)

            return when (element.successful) {
                true -> buildString {
                    appendLine(decorationFormatter(prefix))
                    append(decorationFormatter(bottomLeft), processReturnValue?.format()?.ansi?.bold)
                }
                null -> buildString {
                    val halfLine = decorationFormatter(incompletePrefix)
                    appendLine(halfLine)
                    val formatted: String? = processReturnValue?.run { symbol + (textRepresentation?.withPrefix(" ") ?: "") }
                    if (formatted.isNullOrBlank()) {
                        append(halfLine)
                    } else {
                        appendLine(halfLine)
                        append(formatted.ansi.bold)
                    }
                }
                false -> buildString {
                    appendLine(processReturnValue?.symbol ?: decorationFormatter(prefix))
                    append(decorationFormatter(bottomLeft), processReturnValue?.textRepresentation?.ansi?.bold)
                }
            }
        }
    }

    public object Dotted : BlockStyle {

        private val playSymbol = "▶"
        private val whitePlaySymbol = "▷"
        private val dot = "·"

        override val indent: Int = content("") { it.toString() }?.length ?: 0

        override fun start(element: CharSequence, decorationFormatter: Formatter): CharSequence? = buildString {
            val startElements = element.asAnsiString().lines()
            append(decorationFormatter(playSymbol), " ", decorationFormatter(startElements.first()).ansi.bold)
            startElements.drop(1).forEach { startElement ->
                appendLine()
                append(decorationFormatter(whitePlaySymbol), " ", decorationFormatter(startElement).ansi.bold)
            }
        }

        override fun content(element: CharSequence, decorationFormatter: Formatter): CharSequence? = buildString {
            append(decorationFormatter(dot), " ", element)
        }

        override fun end(element: ReturnValue, resultValueFormatter: (ReturnValue) -> ReturnValue?, decorationFormatter: Formatter): CharSequence? =
            buildString {
                val formatted = resultValueFormatter(element)?.format()
                if (element.successful == false) append(formatted?.ansi?.red?.bold)
                else append(formatted?.ansi?.bold)
            }
    }

    public object None : BlockStyle {
        private val prefix = "    "
        override val indent: Int = prefix.length
        override fun start(element: CharSequence, decorationFormatter: Formatter): CharSequence = element
        override fun content(element: CharSequence, decorationFormatter: Formatter): CharSequence = element
        override fun parent(element: CharSequence, decorationFormatter: Formatter): CharSequence = content(element, decorationFormatter).prefixWith(prefix)

        override fun end(element: ReturnValue, resultValueFormatter: (ReturnValue) -> ReturnValue?, decorationFormatter: Formatter): CharSequence? =
            resultValueFormatter(element)?.format()
    }

    public val DEFAULT: BlockStyle = Rounded
    public fun from(border: Boolean?): Style = when (border) {
        true -> Rounded
        false -> Dotted
        null -> None
    }
}

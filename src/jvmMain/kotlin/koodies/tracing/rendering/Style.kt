package koodies.tracing.rendering

import koodies.text.ANSI.FilteringFormatter
import koodies.text.ANSI.Formatter
import koodies.text.ANSI.Formatter.Companion.ToCharSequence
import koodies.text.LineSeparators
import koodies.text.takeUnlessEmpty

/**
 * A style is a simple generalization attempt
 * to improve separation of concern.
 *
 * This component assumes that what needs to be styled consists of
 * a start and end element and 0 or more content elements.
 */
public interface Style {

    public val onlineLinePrefix: String
    public val onlineLineSeparator: String

    public val indent: Int
    public val layout: ColumnsLayout

    /**
     * Styles the introducing first element.
     *
     * The optional [contentFormatter] will to the headline, [decorationFormatter] will be applied to all
     * "decoration" added.
     */
    public fun start(
        element: CharSequence,
        contentFormatter: FilteringFormatter<CharSequence>,
        decorationFormatter: Formatter<CharSequence> = ToCharSequence,
    ): CharSequence?

    /**
     * Styles a content element.
     *
     * The optional [decorationFormatter] will be applied to all
     * "decoration" added.
     */
    public fun content(
        element: CharSequence,
        decorationFormatter: Formatter<CharSequence> = ToCharSequence,
    ): CharSequence?

    /**
     * Styles an element to be inserted in its parent which
     * is necessary for nested layouts.
     *
     * The optional [decorationFormatter] will be applied to all
     * "decoration" added.
     */
    public fun parent(
        element: CharSequence,
        decorationFormatter: Formatter<CharSequence> = ToCharSequence,
    ): CharSequence? = content(element, decorationFormatter)

    /**
     * Styles the finalizing last element.
     *
     * The optional [decorationFormatter] will be applied to all
     * "decoration" added.
     */
    public fun end(
        element: ReturnValue,
        resultValueFormatter: (ReturnValue) -> ReturnValue?,
        decorationFormatter: Formatter<CharSequence> = ToCharSequence,
    ): CharSequence?

    public fun buildString(block: StringBuilder.() -> Unit): CharSequence? =
        StringBuilder().apply(block).takeUnlessEmpty()

    public fun StringBuilder.append(vararg text: CharSequence?): StringBuilder =
        apply { text.forEach { if (it != null) append(it) } }

    public fun StringBuilder.appendLine(vararg text: CharSequence?): StringBuilder =
        apply { append(*text, LineSeparators.DEFAULT) }
}

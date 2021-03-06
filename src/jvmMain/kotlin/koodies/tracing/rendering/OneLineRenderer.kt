package koodies.tracing.rendering

import koodies.asString
import koodies.exception.toCompactString
import koodies.text.ANSI.FilteringFormatter
import koodies.text.ANSI.Formatter
import koodies.text.ANSI.Text.Companion.ansi
import koodies.text.ANSI.ansiRemoved
import koodies.text.LineSeparators
import koodies.tracing.CurrentSpan
import koodies.tracing.Key.KeyValue
import koodies.tracing.SpanId
import koodies.tracing.TraceId
import koodies.tracing.TracingDsl
import koodies.tracing.spanning

/**
 * Renderer that renders event their primary attribute
 * as specified in [Settings.layout] in a single line.
 */
public class OneLineRenderer(
    private val settings: Settings,
) : Renderer {

    private val style = settings.style(settings.layout, settings.indent)
    private val prefix = style.onlineLinePrefix
    private val separator = style.onlineLineSeparator

    private val messages = mutableListOf<CharSequence>()

    override fun start(traceId: TraceId, spanId: SpanId, name: CharSequence) {
        settings.nameFormatter(name)
            ?.also {
                messages.add(settings.decorationFormatter(prefix))
                messages.add(it)
            }
    }

    override fun event(name: CharSequence, attributes: RenderableAttributes) {
        attributes[settings.layout.primaryKey]
            ?.let { settings.contentFormatter.invoke(it) }
            ?.also {
                messages.add(settings.decorationFormatter(separator))
                messages.add(it)
            }
    }

    override fun exception(exception: Throwable, attributes: RenderableAttributes) {
        settings.contentFormatter.invoke(exception.toCompactString())
            ?.also {
                messages.add(settings.decorationFormatter(separator))
                messages.add(it)
            }
    }

    override fun <R> end(result: Result<R>) {

        val formattedResult = ReturnValue.of(result)
            .let { settings.returnValueTransform(it)?.format() }

        (messages.takeUnless { it.isEmpty() }
            ?.joinToString("") { LineSeparators.unify(it, "⏎") }
            ?.let { "$it $formattedResult" } ?: formattedResult)
            ?.let(settings.printer)
    }

    override fun childRenderer(renderer: RendererProvider): Renderer =
        renderer(settings.copy(printer = ::printChild)) { OneLineRenderer(it) }

    override fun printChild(text: CharSequence) {
        messages.add(" " + text.ansiRemoved.ansi.gray)
    }

    override fun toString(): String = asString {
        ::settings to settings
    }
}

/**
 * Creates a new nested span inside of the currently active span,
 * and runs [block] with this newly creates span as its [CurrentSpan] in the receiver.
 *
 * This method behaves like [spanning] with one difference:
 * Logging is rendered by a [OneLineRenderer].
 */
@TracingDsl
public fun <R> spanningLine(
    name: CharSequence,
    vararg attributes: KeyValue<*, *>,

    nameFormatter: FilteringFormatter<CharSequence>? = null,
    contentFormatter: FilteringFormatter<CharSequence>? = null,
    decorationFormatter: Formatter<CharSequence>? = null,
    returnValueTransform: ((ReturnValue) -> ReturnValue?)? = null,
    layout: ColumnsLayout? = null,
    style: ((ColumnsLayout, Int) -> Style)? = null,
    printer: Printer? = null,

    block: CurrentSpan.() -> R,
): R = spanning(
    name = name,
    attributes = attributes,
    renderer = { OneLineRenderer(this) },
    nameFormatter = nameFormatter,
    contentFormatter = contentFormatter,
    decorationFormatter = decorationFormatter,
    returnValueTransform = returnValueTransform,
    layout = layout,
    style = style,
    printer = printer,
    block = block
)

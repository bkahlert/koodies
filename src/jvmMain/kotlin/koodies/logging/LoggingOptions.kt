package koodies.logging

import koodies.builder.BuilderTemplate
import koodies.builder.context.CapturesMap
import koodies.builder.context.CapturingContext
import koodies.builder.context.SkippableCapturingBuilderInterface
import koodies.logging.FixedWidthRenderingLogger.Border
import koodies.logging.LoggingOptions.BlockLoggingOptions.Companion.BlockLoggingOptionsContext
import koodies.logging.LoggingOptions.CompactLoggingOptions.Companion.CompactLoggingOptionsContext
import koodies.logging.LoggingOptions.Companion.LoggingOptionsContext
import koodies.logging.LoggingOptions.SmartLoggingOptions.Companion.SmartLoggingOptionsContext
import koodies.text.ANSI.Colors
import koodies.text.ANSI.Formatter

/**
 * Options that define how a [RenderingLogger] renders log messages.
 */
public sealed class LoggingOptions {

    public abstract fun newLogger(parent: RenderingLogger?, fallbackCaption: String): RenderingLogger
    public fun logTextCallOrNull(logger: RenderingLogger?): ((String) -> Unit)? = logger?.run { { logText { it } } }

    /**
     * Renders log messages line-by-line.
     */
    public class BlockLoggingOptions(
        public val caption: CharSequence? = null,
        public val contentFormatter: Formatter? = DEFAULT_CONTENT_FORMATTER,
        public val decorationFormatter: Formatter? = DEFAULT_DECORATION_FORMATTER,
        public val returnValueFormatter: ((ReturnValue) -> ReturnValue)? = DEFAULT_RESULT_FORMATTER,
        public val border: Border = Border.DOTTED,
    ) : LoggingOptions() {
        override fun newLogger(parent: RenderingLogger?, fallbackCaption: String): RenderingLogger =
            BlockRenderingLogger(
                caption ?: fallbackCaption,
                logTextCallOrNull(parent),
                contentFormatter,
                decorationFormatter,
                returnValueFormatter,
                border,
                statusInformationColumn = (parent as? FixedWidthRenderingLogger)?.statusInformationColumn,
                statusInformationPadding = (parent as? FixedWidthRenderingLogger)?.statusInformationPadding,
                statusInformationColumns = (parent as? FixedWidthRenderingLogger)?.statusInformationColumns,
            )

        public companion object : BuilderTemplate<BlockLoggingOptionsContext, BlockLoggingOptions>() {

            public class BlockLoggingOptionsContext(override val captures: CapturesMap) : CapturingContext() {
                public val caption: SkippableCapturingBuilderInterface<() -> String, String?> by builder()
                public val contentFormatter: SkippableCapturingBuilderInterface<() -> Formatter?, Formatter?> by builder<Formatter?>() default DEFAULT_CONTENT_FORMATTER
                public val decorationFormatter: SkippableCapturingBuilderInterface<() -> Formatter?, Formatter?> by builder<Formatter?>() default DEFAULT_DECORATION_FORMATTER
                public val returnValueFormatter: SkippableCapturingBuilderInterface<() -> ((ReturnValue) -> ReturnValue)?, ((ReturnValue) -> ReturnValue)?> by builder<((ReturnValue) -> ReturnValue)?>() default DEFAULT_RESULT_FORMATTER
                public var border: Border by setter(Border.DOTTED)
            }

            override fun BuildContext.build(): BlockLoggingOptions = ::BlockLoggingOptionsContext {
                BlockLoggingOptions(::caption.eval(), ::contentFormatter.eval(), ::decorationFormatter.eval(), ::returnValueFormatter.eval(), ::border.eval())
            }
        }
    }

    /**
     * Renders log messages in a single line.
     */
    public class CompactLoggingOptions(
        public val caption: CharSequence? = null,
        public val contentFormatter: Formatter? = DEFAULT_CONTENT_FORMATTER,
        public val decorationFormatter: Formatter? = DEFAULT_DECORATION_FORMATTER,
        public val returnValueFormatter: ((ReturnValue) -> ReturnValue)? = DEFAULT_RESULT_FORMATTER,
    ) : LoggingOptions() {
        override fun newLogger(parent: RenderingLogger?, fallbackCaption: String): RenderingLogger =
            CompactRenderingLogger(
                caption ?: fallbackCaption,
                contentFormatter,
                decorationFormatter,
                returnValueFormatter,
                logTextCallOrNull(parent),
            )

        public companion object : BuilderTemplate<CompactLoggingOptionsContext, CompactLoggingOptions>() {

            public class CompactLoggingOptionsContext(override val captures: CapturesMap) : CapturingContext() {
                public val caption: SkippableCapturingBuilderInterface<() -> String, String?> by builder()
                public val contentFormatter: SkippableCapturingBuilderInterface<() -> Formatter?, Formatter?> by builder<Formatter?>() default DEFAULT_CONTENT_FORMATTER
                public val returnValueFormatter: SkippableCapturingBuilderInterface<() -> ((ReturnValue) -> ReturnValue)?, ((ReturnValue) -> ReturnValue)?> by builder<((ReturnValue) -> ReturnValue)?>() default DEFAULT_RESULT_FORMATTER
            }

            override fun BuildContext.build(): CompactLoggingOptions = ::CompactLoggingOptionsContext {
                CompactLoggingOptions(::caption.eval(), ::contentFormatter.eval(), DEFAULT_DECORATION_FORMATTER, ::returnValueFormatter.eval())
            }
        }
    }

    /**
     * Renders log messages depending on how many messages are logged.
     *
     * Renders like [Block] unless nothing but a result is logged. In the latter case renders like [Compact].
     */
    public class SmartLoggingOptions(
        public val caption: CharSequence? = null,
        public val contentFormatter: Formatter? = DEFAULT_CONTENT_FORMATTER,
        public val decorationFormatter: Formatter? = DEFAULT_DECORATION_FORMATTER,
        public val returnValueFormatter: ((ReturnValue) -> ReturnValue)? = DEFAULT_RESULT_FORMATTER,
        public val border: Border = Border.DOTTED,
    ) : LoggingOptions() {
        override fun newLogger(parent: RenderingLogger?, fallbackCaption: String): RenderingLogger =
            SmartRenderingLogger(
                caption ?: fallbackCaption,
                logTextCallOrNull(parent),
                contentFormatter,
                decorationFormatter,
                returnValueFormatter,
                border,
                statusInformationColumn = (parent as? FixedWidthRenderingLogger)?.statusInformationColumn,
                statusInformationPadding = (parent as? FixedWidthRenderingLogger)?.statusInformationPadding,
                statusInformationColumns = (parent as? FixedWidthRenderingLogger)?.statusInformationColumns,
                prefix = (parent as? FixedWidthRenderingLogger)?.prefix ?: "",
            )

        public companion object : BuilderTemplate<SmartLoggingOptionsContext, SmartLoggingOptions>() {

            public class SmartLoggingOptionsContext(override val captures: CapturesMap) : CapturingContext() {
                public val caption: SkippableCapturingBuilderInterface<() -> String, String?> by builder()
                public val contentFormatter: SkippableCapturingBuilderInterface<() -> Formatter?, Formatter?> by builder<Formatter?>() default DEFAULT_CONTENT_FORMATTER
                public val decorationFormatter: SkippableCapturingBuilderInterface<() -> Formatter?, Formatter?> by builder<Formatter?>() default DEFAULT_DECORATION_FORMATTER
                public val returnValueFormatter: SkippableCapturingBuilderInterface<() -> ((ReturnValue) -> ReturnValue)?, ((ReturnValue) -> ReturnValue)?> by builder<((ReturnValue) -> ReturnValue)?>() default DEFAULT_RESULT_FORMATTER
                public var border: Border by setter(Border.DOTTED)
            }

            override fun BuildContext.build(): SmartLoggingOptions = ::SmartLoggingOptionsContext {
                SmartLoggingOptions(::caption.eval(), ::contentFormatter.eval(), ::decorationFormatter.eval(), ::returnValueFormatter.eval(), ::border.eval())
            }
        }
    }

    public companion object : BuilderTemplate<LoggingOptionsContext, LoggingOptions>() {

        public val DEFAULT_CONTENT_FORMATTER: Formatter = Formatter.PassThrough
        public val DEFAULT_DECORATION_FORMATTER: Formatter = Colors.brightBlue
        public val DEFAULT_RESULT_FORMATTER: (ReturnValue) -> ReturnValue = { it }

        public class LoggingOptionsContext(override val captures: CapturesMap) : CapturingContext() {
            public val block: SkippableCapturingBuilderInterface<BlockLoggingOptionsContext.() -> Unit, BlockLoggingOptions?> by BlockLoggingOptions
            public val compact: SkippableCapturingBuilderInterface<CompactLoggingOptionsContext.() -> Unit, CompactLoggingOptions?> by CompactLoggingOptions
            public val smart: SkippableCapturingBuilderInterface<SmartLoggingOptionsContext.() -> Unit, SmartLoggingOptions?> by SmartLoggingOptions
        }

        override fun BuildContext.build(): LoggingOptions = ::LoggingOptionsContext {
            ::block.evalOrNull<BlockLoggingOptions>()
                ?: ::compact.evalOrNull<CompactLoggingOptions>()
                ?: ::smart.evalOrNull<SmartLoggingOptions>()
                ?: SmartLoggingOptions()
        }
    }
}

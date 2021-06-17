package koodies.tracing

import koodies.jvm.currentThread
import koodies.jvm.orNull
import koodies.test.get
import koodies.test.isVerbose
import koodies.test.put
import koodies.test.storeForNamespaceAndTest
import koodies.test.testName
import koodies.text.ANSI.Colors
import koodies.text.ANSI.Formatter
import koodies.text.ANSI.Text.Companion.ansi
import koodies.text.Semantics.formattedAs
import koodies.text.padStartFixedLength
import koodies.time.Now
import koodies.time.minutes
import koodies.time.seconds
import koodies.tracing.Span.State.Ended
import koodies.tracing.rendering.BlockRenderer
import koodies.tracing.rendering.InMemoryPrinter
import koodies.tracing.rendering.Printer
import koodies.tracing.rendering.Renderer
import koodies.tracing.rendering.Settings
import koodies.tracing.rendering.TeePrinter
import koodies.unit.milli
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Store
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver
import org.opentest4j.AssertionFailedError
import org.opentest4j.IncompleteExecutionException
import org.opentest4j.MultipleFailuresError
import org.opentest4j.TestAbortedException
import org.opentest4j.TestSkippedException
import strikt.api.Assertion.Builder
import strikt.api.expectThat
import java.time.Instant
import kotlin.time.Duration

class TestSpan(
    name: CharSequence,
    private val clientPrinter: InMemoryPrinter,
    printToConsole: Boolean,
    private val renderer: TestRenderer = TestRenderer(name, clientPrinter, printToConsole),
) : Span by OpenTelemetrySpan(name, renderer = renderer) {

    fun reportTestResult(exception: Throwable?) {
        end(exception)
        renderer.reportTestResult(exception)
    }

    /**
     * Returns a [Builder] to run assertions on what was rendered.
     */
    fun expectThatRendered() =
        expectThat(clientPrinter.toString())

    /**
     * Runs the specified [assertions] on what was rendered.
     */
    fun expectThatRendered(assertions: Builder<String>.() -> Unit) =
        expectThat(clientPrinter.toString(), assertions)
}


/**
 * Resolves a new [TestSpan] with rendering capabilities.
 *
 * @see TestTelemetry
 */
class TestSpanParameterResolver : TypeBasedParameterResolver<TestSpan>(), AfterEachCallback {
    private val store: ExtensionContext.() -> Store by storeForNamespaceAndTest()

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): TestSpan {
        val clientPrinter = InMemoryPrinter()
        return TestSpan(
            name = extensionContext.testName,
            clientPrinter = clientPrinter,
            printToConsole = extensionContext.isVerbose || parameterContext.isVerbose
        )
            .also { extensionContext.store().put(it) }
            .also { it.start() }
    }

    override fun afterEach(extensionContext: ExtensionContext) {
        extensionContext.store().get<TestSpan>()?.reportTestResult(extensionContext.executionException.orNull())
    }
}


/**
 * Renders a [TestSpan] by passing all events to a [TestPrinter].
 * An event stream filtered to the ones actually triggered by the client
 * are passed to the given [printer].
 */
class TestRenderer(
    private val name: CharSequence,
    printer: Printer,
    printToConsole: Boolean,
) : Renderer {

    private val testOnlyPrinter: Printer = if (printToConsole) TestPrinter() else run { {} }
    private val printer: Printer = TeePrinter(testOnlyPrinter, printer)

    override fun start(traceId: TraceId, spanId: SpanId, timestamp: Instant) {
        testOnlyPrinter(TestPrinter.TestIO.Start(name, traceId, spanId))
    }

    override fun event(name: CharSequence, attributes: Map<CharSequence, CharSequence>, timestamp: Instant) {
        printer("$name: $attributes")
    }

    override fun exception(exception: Throwable, attributes: Map<CharSequence, CharSequence>, timestamp: Instant) {
        printer("$exception: $attributes")
    }

    override fun end(ended: Ended) {
        // not interested in any client-side triggered end result, but in the test result
    }

    fun reportTestResult(exception: Throwable?) {
        when (exception) {
            null -> testOnlyPrinter(TestPrinter.TestIO.Pass)
            else -> testOnlyPrinter(TestPrinter.TestIO.Fail(exception))
        }
    }

    override fun nestedRenderer(name: CharSequence, customize: Settings.() -> Settings): Renderer {
        return BlockRenderer(name, Settings().customize()) { printer(it) }
    }

    override fun nestedRenderer(provider: (Settings, Printer) -> Renderer): Renderer {
        return provider(Settings()) { printer(it) }
    }
}


/**
 * Printer that prepends each line with runtime information.
 */
class TestPrinter : Printer {

    private val start: Long by lazy { Now.millis }

    private val breakPoints = mutableListOf(
        100.milli.seconds,
        120.milli.seconds,
        130.milli.seconds,
        200.milli.seconds,
        500.milli.seconds,
        1.seconds,
        5.seconds,
        10.seconds,
        30.seconds,
        1.minutes,
        2.minutes,
        5.minutes,
        10.minutes,
        15.minutes,
        30.minutes,
        45.minutes,
        60.minutes,
    )

    override fun invoke(text: CharSequence) {
        val timePassed = Now.passedSince(start)
        when (text) {
            is TestIO.Start -> {
                println()
                print("TraceID ".meta)
                print(text.traceId.toString().ansi.gray)
                print("   ")
                println(text)
                println(headerLine)
            }
            is TestIO.Pass, is TestIO.Fail -> {
                println(footerLine)
                print(resultPrefix)
                print(timePassed.toString().padStartFixedLength(7).formattedAs.meta)
                print("   ")
                println(text)
            }
            else -> {
                val thread = currentThread.name.padStartFixedLength(31)
                val time = timePassed.format()
                val prefix = "$thread  $time │ ".meta
                text.lineSequence().forEach { println("$prefix$it") }
            }
        }
    }

    private fun Duration.format(): CharSequence {
        val formatted = toString().padStartFixedLength(7)
        return if (breakPoints.takeWhile { it < this }.also { breakPoints.removeAll(it) }.isNotEmpty()) {
            formatted.ansi.gray.bold
        } else {
            formatted
        }
    }

    sealed class TestIO(private val string: String) : CharSequence by string {
        class Start(name: CharSequence, val traceId: TraceId, val spanId: SpanId) : TestIO(name.toString())
        object Pass : TestIO("Pass".formattedAs.success)
        class Fail(val exception: Throwable) : TestIO(
            when (exception) {
                is AssertionFailedError -> "Fail"
                is IncompleteExecutionException -> "Incomplete"
                is MultipleFailuresError -> "Fail (Multi)"
                is TestAbortedException -> "Abort"
                is TestSkippedException -> "Skip"
                else -> "Crash"
            }.formattedAs.failure
        )

        override fun toString(): String = string
    }

    private companion object {
        private val formatter = Formatter { it.ansi.color(Colors.gray(.45)) }
        val CharSequence.meta: CharSequence get() = formatter(this)
        val headerLine = StringBuilder().apply {
            append("─".repeat(41))
            append("┬".repeat(1))
            append("─".repeat(81))
        }.meta
        val footerLine = StringBuilder().apply {
            append("─".repeat(41))
            append("┴".repeat(1))
            append("─".repeat(81))
        }.meta
        val resultPrefix = StringBuilder().apply {
            append(" ".repeat(31))
            append(" ".repeat(2))
        }.meta
    }
}

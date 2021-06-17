package koodies.tracing

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import koodies.collections.synchronizedMapOf
import koodies.test.testName
import koodies.tracing.OpenTelemetrySpan.Companion.toAttributes
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan
import strikt.api.Assertion.Builder
import strikt.api.expectThat
import io.opentelemetry.api.OpenTelemetry as OpenTelemetryAPI

/**
 * [OpenTelemetry] integration in JUnit that run OpenTelemetry
 * along a test plan execution and provides means to assess recorded data.
 */
class TestTelemetry : TestExecutionListener, TypeBasedParameterResolver<Span>() {

    private lateinit var batchExporter: BatchSpanProcessor

    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        if (ENABLED) {
            val jaegerExporter = JaegerGrpcSpanExporter.builder()
                .setEndpoint(Jaeger.startLocally())
                .build()

            batchExporter = BatchSpanProcessor.builder(jaegerExporter).build()

            val tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(InMemoryStoringSpanProcessor)
                .addSpanProcessor(batchExporter)
                .setResource(mapOf("service.name" to "koodies-test").toResource())
                .build()

            val openTelemetry: OpenTelemetryAPI = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal()

            OpenTelemetry.register(openTelemetry)
        } else {
            OpenTelemetry.register(OpenTelemetryAPI.noop())
        }
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan?) {
        if (ENABLED) {
            batchExporter.shutdown()
        }
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Span =
        OpenTelemetrySpan(extensionContext.testName).also { it.start() }

    companion object {

        const val ENABLED: Boolean = true

        private val traces = synchronizedMapOf<TraceId, MutableList<SpanData>>()

        private object InMemoryStoringSpanProcessor : SpanProcessor {
            override fun isStartRequired(): Boolean = false
            override fun onStart(parentContext: Context?, span: ReadWriteSpan?): Unit = Unit
            override fun isEndRequired(): Boolean = true
            override fun onEnd(span: ReadableSpan) {
                val spanData = span.toSpanData()
                traces.getOrPut(TraceId(spanData.traceId)) { mutableListOf() }.add(spanData)
            }
        }

        /**
         * Returns the trace recorded for the given [spanId].
         */
        operator fun get(traceId: TraceId): List<SpanData> =
            traces.getOrDefault(traceId, emptyList())

        @Suppress("NOTHING_TO_INLINE")
        inline fun Map<out CharSequence, CharSequence>.toResource(): Resource =
            Resource.create(toAttributes())
    }
}

private val noopTraceId = TraceId("0".repeat(32))
val TraceId.Companion.NOOP get() = noopTraceId

private val noopSpanId = SpanId("0".repeat(16))
val SpanId.Companion.NOOP get() = noopSpanId

/**
 * Ends the current spans and returns a [Builder] to run assertions on the recorded [SpanData].
 */
fun endCurrentAndExpect(assertions: Builder<List<SpanData>>.() -> Unit) {
    val currentSpan = io.opentelemetry.api.trace.Span.current()
    currentSpan.end()
    expectThat(TestTelemetry[currentSpan.traceId], assertions)
}

/**
 * Ends this spans and returns a [Builder] to run assertions on the recorded [SpanData].
 */
fun Span.endAndExpect() =
    expectThat(TestTelemetry[end()])

/**
 * Ends this spans and runs the specified [assertions] on the recorded [SpanData].
 */
fun Span.endAndExpect(assertions: Builder<List<SpanData>>.() -> Unit) {
    expectThat(TestTelemetry[end()], assertions)
}

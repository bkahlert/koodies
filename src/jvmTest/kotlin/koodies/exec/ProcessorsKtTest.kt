package koodies.exec

import koodies.Koodies
import koodies.collections.synchronizedListOf
import koodies.exec.Process.ExitState
import koodies.exec.ProcessingMode.Interactivity.Interactive
import koodies.exec.ProcessingMode.Interactivity.NonInteractive
import koodies.exec.ProcessingMode.Synchronicity.Async
import koodies.exec.ProcessingMode.Synchronicity.Sync
import koodies.junit.UniqueId
import koodies.test.hasElements
import koodies.test.toStringIsEqualTo
import koodies.test.withTempDir
import koodies.text.LineSeparators.LF
import koodies.time.seconds
import koodies.tracing.TraceId
import koodies.tracing.eventText
import koodies.tracing.events
import koodies.tracing.expectTraced
import koodies.tracing.hasSpanAttribute
import koodies.tracing.spanName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import kotlin.time.Duration
import kotlin.time.measureTime

class ProcessorsKtTest {

    @Nested
    inner class SynchronousProcessing {

        @Nested
        inner class SpanningProcessor {

            @Test
            fun `should trace`() {
                CommandLine("tee", "/dev/fd/2").toExec()
                    .process(ProcessingMode(Sync, NonInteractive("Hello Cat!${LF}".byteInputStream())), Processors.spanningProcessor(
                        ExecAttributes.NAME to "exec-name",
                        ExecAttributes.EXECUTABLE to CommandLine("cat"),
                    ))

                TraceId.current.expectTraced().hasElements(
                    {
                        spanName.isEqualTo("exec-name")
                        hasSpanAttribute(ExecAttributes.NAME, "exec-name")
                        hasSpanAttribute(ExecAttributes.EXECUTABLE, "cat")
                        events.hasElements(
                            { eventText.isEqualTo("Hello Cat!") },
                            { eventText.isEqualTo("Hello Cat!") },
                        )
                    }
                )
            }
        }

        @Nested
        inner class NonInteractively {

            @Test
            fun `should process with no input`() {
                val log = mutableListOf<IO>()
                CommandLine("echo", "Hello World!").toExec()
                    .process(ProcessingMode(Sync, NonInteractive(null))) { _: Exec, callback: ((IO) -> Unit) -> ExitState ->
                        callback { log.add(it) }
                    }
                expectThat(log)
                    .with({ size }) { isEqualTo(1) }
                    .with({ get(0) }) { isA<IO.Output>().toStringIsEqualTo("Hello World!") }
            }

            @Test
            fun `should process with input`() {
                val log = mutableListOf<IO>()
                CommandLine("cat").toExec()
                    .process(ProcessingMode(Sync, NonInteractive("Hello Cat!$LF".byteInputStream()))) { _: Exec, callback: ((IO) -> Unit) -> ExitState ->
                        callback { log.add(it) }
                    }
                expectThat(log)
                    .with({ size }) { isEqualTo(1) }
                    .with({ get(0) }) { isA<IO.Output>().toStringIsEqualTo("Hello Cat!") }
            }
        }


        @Nested
        inner class Interactively {

            @Test
            fun `should process with non-blocking reader`(uniqueId: UniqueId) = withTempDir(uniqueId) {
                val log = mutableListOf<IO>()
                CommandLine("/bin/sh", "-c", "read input; echo \"\$input you, too\"").toExec()
                    .also { it.enter("Hello Back!", delay = Duration.ZERO) }
                    .process(ProcessingMode(Sync, Interactive(nonBlocking = true))) { _: Exec, callback: ((IO) -> Unit) -> ExitState ->
                        callback { log.add(it) }
                    }
                expectThat(log)
                    .with({ size }) { isEqualTo(2) }
                    .with({ get(0) }) { isA<IO.Output>().toStringIsEqualTo("Hello Back!") }
                    .with({ get(1) }) { isA<IO.Output>().toStringIsEqualTo(" you, too") }
            }

            @Test
            fun `should process with blocking reader`(uniqueId: UniqueId) = withTempDir(uniqueId) {
                val log = mutableListOf<IO>()
                CommandLine("/bin/sh", "-c", "read input; echo \"\$input you, too\"").toExec()
                    .also { it.enter("Hello Back!", delay = Duration.ZERO) }
                    .process(ProcessingMode(Sync, Interactive(nonBlocking = false))) { _: Exec, callback: ((IO) -> Unit) -> ExitState ->
                        callback { log.add(it) }
                    }

                expectThat(log)
                    .with({ size }) { isEqualTo(2) }
                    .with({ get(0) }) { isA<IO.Output>().toStringIsEqualTo("Hello Back!") }
                    .with({ get(1) }) { isA<IO.Output>().toStringIsEqualTo(" you, too") }
            }
        }
    }

    @Nested
    inner class AsynchronousProcessing {

        @Nested
        inner class SpanningProcessor {

            @Test
            fun `should trace`() {
                CommandLine("tee", "/dev/fd/2").toExec()
                    .process(ProcessingMode(Async, NonInteractive("Hello Cat!$LF".repeat(3).byteInputStream())), Processors.spanningProcessor(
                        ExecAttributes.NAME to "exec-name",
                        ExecAttributes.EXECUTABLE to CommandLine("cat"),
                    )).waitFor()

                TraceId.current.expectTraced().hasElements(
                    {
                        spanName.isEqualTo("exec-name")
                        hasSpanAttribute(ExecAttributes.NAME, "exec-name")
                        hasSpanAttribute(ExecAttributes.EXECUTABLE, "cat")
                        events.hasElements(
                            { eventText.isEqualTo("Hello Cat!") },
                            { eventText.isEqualTo("Hello Cat!") },
                        )
                    }
                )
            }
        }

        @Nested
        inner class NonInteractively {

            @Nested
            inner class WaitingForTermination {

                @Test
                fun `should process with no input`() {
                    val log = synchronizedListOf<IO>()
                    CommandLine("echo", "Hello World!").toExec()
                        .process(ProcessingMode(Async, NonInteractive(null))) { _: Exec, callback: ((IO) -> Unit) -> ExitState ->
                            callback { log.add(it) }
                        }.waitFor()
                    expectThat(log)
                        .with({ size }) { isEqualTo(1) }
                        .with({ get(0) }) { isA<IO.Output>().toStringIsEqualTo("Hello World!") }
                }

                @Test
                fun `should process with input`() {
                    val log = synchronizedListOf<IO>()
                    CommandLine("cat").toExec()
                        .process(ProcessingMode(Async, NonInteractive("Hello Cat!$LF".byteInputStream()))) { _: Exec, callback: ((IO) -> Unit) -> ExitState ->
                            callback { log.add(it) }
                        }.waitFor()
                    expectThat(log)
                        .with({ size }) { isEqualTo(1) }
                        .with({ get(0) }) { isA<IO.Output>().toStringIsEqualTo("Hello Cat!") }
                }
            }

            @Nested
            inner class NotWaitingForTermination {

                @Test
                fun `should process with no input`() {
                    val timePassed = measureTime {
                        CommandLine("sleep", "10").toExec().process(ProcessingMode(Async, NonInteractive(null)))
                    }
                    expectThat(timePassed).isLessThan(0.5.seconds)
                }

                @Test
                fun `should process with input`() {
                    val timePassed = measureTime {
                        CommandLine("cat").toExec().process(ProcessingMode(Async, NonInteractive("Hello Cat!$LF".byteInputStream())))
                    }
                    expectThat(timePassed).isLessThan(0.5.seconds)
                }
            }
        }

        @Nested
        inner class Interactively {

            @Nested
            inner class WaitingForTermination {

                @Test
                fun `should process with non-blocking reader`() {
                    val log = synchronizedListOf<IO>()
                    CommandLine("/bin/sh", "-c", "read input; echo \"\$input you, too\"").toExec()
                        .also { it.enter("Hello Back!", delay = Duration.ZERO) }
                        .process(ProcessingMode(Async, Interactive(nonBlocking = true))) { _: Exec, callback: ((IO) -> Unit) -> ExitState ->
                            callback { log.add(it) }
                        }.waitFor()
                    expectThat(log)
                        .with({ size }) { isEqualTo(2) }
                        .with({ get(0) }) { isA<IO.Output>().toStringIsEqualTo("Hello Back!") }
                        .with({ get(1) }) { isA<IO.Output>().toStringIsEqualTo(" you, too") }
                }

                @Test
                fun `should process with blocking reader`() {
                    val log = synchronizedListOf<IO>()
                    CommandLine("/bin/sh", "-c", "read input; echo \"\$input you, too\"").toExec()
                        .also { it.enter("Hello Back!", delay = Duration.ZERO) }
                        .process(ProcessingMode(Async, Interactive(nonBlocking = false))) { _: Exec, callback: ((IO) -> Unit) -> ExitState ->
                            callback { log.add(it) }
                        }.waitFor()
                    expectThat(log)
                        .with({ size }) { isEqualTo(2) }
                        .with({ get(0) }) { isA<IO.Output>().toStringIsEqualTo("Hello Back!") }
                        .with({ get(1) }) { isA<IO.Output>().toStringIsEqualTo(" you, too") }
                }
            }

            @Nested
            inner class NotWaitingForTermination {

                @Test
                fun `should process with non-blocking reader`() {
                    val timePassed = measureTime {
                        CommandLine("sleep", "10").toExec()
                            .also { it.enter("Hello Back!", delay = Duration.ZERO) }
                            .process(ProcessingMode(Async, Interactive(nonBlocking = true)))
                    }
                    expectThat(timePassed).isLessThan(.5.seconds)
                }

                @Test
                fun `should process with blocking reader`() {
                    val timePassed = measureTime {
                        CommandLine("sleep", "10").toExec()
                            .also { it.enter("Hello Back!", delay = Duration.ZERO) }
                            .process(ProcessingMode(Async, Interactive(nonBlocking = false)))
                    }
                    expectThat(timePassed).isLessThan(.5.seconds)
                }
            }
        }
    }

    @Nested
    inner class SpanningProcessor {

        @Test
        fun `should process exec and IO`() {
            lateinit var capturedExec: Exec
            lateinit var capturedIO: IO
            val commandLine = CommandLine("echo", "Hello World!")
            commandLine.toExec().process(processor = Processors.spanningProcessor { exec, io ->
                capturedExec = exec
                capturedIO = io
            })
            expectThat(capturedExec.commandLine).isEqualTo(commandLine)
            expectThat(capturedIO).isEqualTo(IO.Output typed "Hello World!")
        }
    }
}

private fun CommandLine.toExec() = toExec(false, emptyMap(), Koodies.InternalTemp, null)

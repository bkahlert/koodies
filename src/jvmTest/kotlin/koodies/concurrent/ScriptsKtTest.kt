package koodies.concurrent

import koodies.concurrent.process.IO
import koodies.concurrent.process.IO.Type.ERR
import koodies.concurrent.process.IO.Type.OUT
import koodies.concurrent.process.ManagedProcess
import koodies.concurrent.process.Processor
import koodies.concurrent.process.Processors
import koodies.concurrent.process.containsDump
import koodies.concurrent.process.logged
import koodies.concurrent.process.processSynchronously
import koodies.logging.InMemoryLogger
import koodies.logging.RenderingLogger
import koodies.shell.ShellScript
import koodies.terminal.contains
import koodies.test.SystemIoExclusive
import koodies.test.SystemIoRead
import koodies.test.UniqueId
import koodies.test.output.CapturedOutput
import koodies.test.testWithTempDir
import koodies.test.withTempDir
import koodies.text.matchesCurlyPattern
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isTrue
import java.nio.file.Path
import java.util.concurrent.CompletionException

@Execution(CONCURRENT)
class ScriptsKtTest {

    private val echoingCommands =
        "echo \"test output ${'$'}TEST\"; sleep 1; >&2 echo \"test error 1\"; sleep 1; echo \"test output 2\"; sleep 1; >&2 echo \"test error 2\""

    private fun getFactories(
        scriptContent: String = echoingCommands,
        processor: Processor<ManagedProcess>? = Processors.noopProcessor(),
        logger: RenderingLogger? = InMemoryLogger(),
    ) = listOf<Path.() -> ManagedProcess>(
        {
            processor?.let { script(ShellScript { !scriptContent }, mapOf("TEST" to "env"), processor = processor) }
                ?: script(ShellScript { !scriptContent }, mapOf("TEST" to "env"))
        },
        {
            processor?.let { script(processor, mapOf("TEST" to "env")) { !scriptContent } }
                ?: script(environment = mapOf("TEST" to "env")) { !scriptContent }
        },
        {
            script(logger, mapOf("TEST" to "env")) { !scriptContent }
        },
    )

    @Nested
    inner class ScriptFn {

        @TestFactory
        fun `should start implicitly`(uniqueId: UniqueId) = getFactories().testWithTempDir(uniqueId) { processFactory ->
            val process = processFactory()
            expectThat(process.started).isTrue()
        }

        @TestFactory
        fun `should start`(uniqueId: UniqueId) = getFactories().testWithTempDir(uniqueId) { processFactory ->
            val process = processFactory()
            process.start()
            expectThat(process.started).isTrue()
        }

        @TestFactory
        fun `should process`(uniqueId: UniqueId) = listOf<Path.(Processor<ManagedProcess>) -> ManagedProcess>(
            { script(ShellScript { !echoingCommands }, mapOf("TEST" to "env"), processor = it) },
            { script(it, mapOf("TEST" to "env")) { !echoingCommands } },
            { processor ->
                val logger = InMemoryLogger()
                val process = script(logger, mapOf("TEST" to "env")) { !echoingCommands }
                logger.logged.lines().forEach { line ->
                    if (line.contains("test output env")) process.processor(OUT typed "test output env")
                    if (line.contains("test output 2")) process.processor(OUT typed "test output 2")
                    if (line.contains("test error 1")) process.processor(ERR typed "test error 1")
                    if (line.contains("test error 2")) process.processor(ERR typed "test error 2")
                }
                process
            },
        ).testWithTempDir(uniqueId) { processFactory ->
            val processed = mutableListOf<IO>()
            processFactory { io -> processed.add(io) }
            expectThat(processed).contains(
                OUT typed "test output env",
                OUT typed "test output 2",
                ERR typed "test error 1",
                ERR typed "test error 2",
            )
        }

        @TestFactory
        fun `should throw on unexpected exit value`(uniqueId: UniqueId) = getFactories("exit 42").testWithTempDir(uniqueId) { processFactory ->
            expectCatching { processFactory() }
                .isFailure()
                .isA<CompletionException>()
                .with({ message }) { isNotNull() and { containsDump() } }
        }
    }

    @Nested
    inner class OutputFn {

        @TestFactory
        fun `should run process synchronously implicitly`(uniqueId: UniqueId) = getFactories().testWithTempDir(uniqueId) { processFactory ->
            val process = processFactory()
            expectThat(process.exitValue).isEqualTo(0)
        }

        @TestFactory
        fun `should log IO`(uniqueId: UniqueId) = getFactories().testWithTempDir(uniqueId) { processFactory ->
            val process = processFactory()
            process.output()

            expectThat(process.ioLog.logged.drop(2).dropLast(1))
                .containsExactlyInAnyOrder(
                    OUT typed "test output env",
                    ERR typed "test error 1",
                    OUT typed "test output 2",
                    ERR typed "test error 2",
                )
        }

        @TestFactory
        fun `should contain OUT`(uniqueId: UniqueId) = getFactories().testWithTempDir(uniqueId) { processFactory ->
            val output = processFactory().output()

            expectThat(output).isEqualTo("""
                test output env
                test output 2
            """.trimIndent())
        }
    }

    @Nested
    inner class ScriptOutputContains {

        @Test
        fun `should assert present string`(uniqueId: UniqueId) = withTempDir(uniqueId) {
            expectThat(scriptOutputContains("echo 'this is a test'", "Test", caseSensitive = false)).isTrue()
        }

        @Test
        fun `should assert missing string`(uniqueId: UniqueId) = withTempDir(uniqueId) {
            expectThat(scriptOutputContains("echo 'this is a test'", "Missing", caseSensitive = false)).isFalse()
        }

        @Test
        fun `should assert present string case-sensitive`(uniqueId: UniqueId) = withTempDir(uniqueId) {
            expectThat(scriptOutputContains("echo 'this is a test'", "test", caseSensitive = true)).isTrue()
        }

        @Test
        fun `should assert missing string case-sensitive`(uniqueId: UniqueId) = withTempDir(uniqueId) {
            expectThat(scriptOutputContains("echo 'this is a test'", "Test", caseSensitive = true)).isFalse()
        }
    }

    @Nested
    inner class SynchronousExecution {

        @SystemIoExclusive
        @TestFactory
        fun `should process log to console by default`(output: CapturedOutput, uniqueId: UniqueId) =
            getFactories(processor = null, logger = null).testWithTempDir(uniqueId) { processFactory ->
                processFactory()
                expectThat(output).get { out }.matchesCurlyPattern("""
                ▶{}commandLine{{}}
                · Executing {{}}
                · {} file:{}
                · test output env
                · test output 2
                · test error 1
                · test error 2{{}}
                """.trimIndent())
                expectThat(output).get { err }.isEmpty()
            }

        @SystemIoRead
        @TestFactory
        fun `should process not log to console if specified`(output: CapturedOutput, uniqueId: UniqueId) =
            getFactories(processor = {}, logger = InMemoryLogger()).testWithTempDir(uniqueId) { processFactory ->
                val process = processFactory()
                process.processSynchronously(Processors.noopProcessor())
                expectThat(output).get { out }.isEmpty()
                expectThat(output).get { err }.isEmpty()
            }

        @SystemIoExclusive
        @TestFactory
        fun `should format merged output`(uniqueId: UniqueId) =
            getFactories(processor = {}, logger = null).testWithTempDir(uniqueId) { processFactory ->
                val process = processFactory()
                process.processSynchronously(Processors.noopProcessor())
                expectThat(process.logged).matchesCurlyPattern("""
                    Executing {}
                    {} file:{}
                    test output env
                    test output 2
                    test error 1
                    test error 2
                    Process {} terminated successfully at {}.
                    """.trimIndent())
            }
    }
}

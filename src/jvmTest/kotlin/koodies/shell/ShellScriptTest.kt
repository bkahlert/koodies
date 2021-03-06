package koodies.shell

import koodies.docker.DockerContainer
import koodies.docker.DockerImage
import koodies.docker.DockerRunCommandLine
import koodies.docker.DockerRunCommandLine.Options
import koodies.docker.DockerStopCommandLine
import koodies.docker.MountOptions
import koodies.exec.CommandLine
import koodies.exec.exitCodeOrNull
import koodies.io.path.asPath
import koodies.io.path.hasContent
import koodies.io.path.pathString
import koodies.io.path.writeBytes
import koodies.io.randomFile
import koodies.junit.UniqueId
import koodies.shell.ShellScript.Companion.isScript
import koodies.shell.ShellScript.ScriptContext
import koodies.test.Smoke
import koodies.test.string
import koodies.test.testEach
import koodies.test.tests
import koodies.test.toStringIsEqualTo
import koodies.test.withTempDir
import koodies.text.LineSeparators.LF
import koodies.text.joinLinesToString
import koodies.text.lines
import koodies.text.matchesCurlyPattern
import koodies.text.toByteArray
import koodies.text.toStringMatchesCurlyPattern
import koodies.time.seconds
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.Assertion.Builder
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.java.exists
import strikt.java.isExecutable
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import koodies.text.Unicode.ESCAPE as e

class ShellScriptTest {

    private fun shellScript(name: String? = "Test") = ShellScript(name) {
        shebang
        changeDirectoryOrExit(Path.of("/some/where"))
        echo("Hello World!")
        echo("Bye!")
        exit(42)
    }

    @Test
    fun `should build valid script`() {
        //language=Shell Script
        expectThat(shellScript()).toStringIsEqualTo("""
            #!/bin/sh
            cd "/some/where" || exit 1
            'echo' 'Hello World!'
            'echo' 'Bye!'
            'exit' '42'

        """.trimIndent(), removeAnsi = false)
    }

    @Test
    fun `should build trim indent content`() {
        expectThat(ShellScript("    echo '👈 no padding'"))
            .toStringIsEqualTo("echo '👈 no padding'$LF")
    }

    @TestFactory
    fun `should build with simple string`() = tests {
        expecting {
            ShellScript { "printenv HOME" }
        } that {
            toStringIsEqualTo("printenv HOME$LF")
        }

        expecting {
            ShellScript { shebang; "printenv HOME" }
        } that {
            toStringIsEqualTo("#!/bin/sh${LF}printenv HOME$LF")
        }
    }

    @Test
    fun `should write valid script`(uniqueId: UniqueId) = withTempDir(uniqueId) {
        val file = randomFile(extension = ".sh")
        shellScript().toFile(file)
        //language=Shell Script
        expectThat(file).hasContent("""
            #!/bin/sh
            cd "/some/where" || exit 1
            'echo' 'Hello World!'
            'echo' 'Bye!'
            'exit' '42'

        """.trimIndent())
    }

    @Test
    fun `should write executable script`(uniqueId: UniqueId) = withTempDir(uniqueId) {
        val file = randomFile(extension = ".sh")
        val returnedScript = shellScript().toFile(file)
        expectThat(returnedScript).isExecutable()
    }

    @Test
    fun `should return same file as saved to file`(uniqueId: UniqueId) = withTempDir(uniqueId) {
        val file = randomFile(extension = ".sh")
        val returnedScript = shellScript().toFile(file)
        expectThat(returnedScript).isEqualTo(file)
    }

    @Nested
    inner class Name {

        private val testBanner = "$e[90;40m░$e[39;49m$e[96;46m░$e[39;49m" +
            "$e[94;44m░$e[39;49m$e[92;42m░$e[39;49m$e[93;43m░" +
            "$e[39;49m$e[95;45m░$e[39;49m$e[91;41m░$e[39;49m " +
            "$e[96mTEST$e[39m"

        private val differentBanner = "$e[90;40m░$e[39;49m$e[96;46m░$e[39;49m" +
            "$e[94;44m░$e[39;49m$e[92;42m░$e[39;49m$e[93;43m░" +
            "$e[39;49m$e[95;45m░$e[39;49m$e[91;41m░$e[39;49m " +
            "$e[96mDIFFERENT$e[39m"

        @Test
        fun `should not echo name`() {
            val sh = ShellScript("test", "exit 0")
            expectThat(sh.toString()).isEqualTo("""
                exit 0
    
            """.trimIndent())
        }

        @Test
        fun `should echo name if specified`() {
            val sh = ShellScript("test", "exit 0")
            expectThat(sh.toString(echoName = true)).toStringIsEqualTo("""
                echo '$testBanner'
                exit 0
    
            """.trimIndent())
        }

        @Test
        fun `should use different name if specified`() {
            val sh = ShellScript("test", "exit 0")
            expectThat(sh.toString(true, "different")).isEqualTo("""
                echo '$differentBanner'
                exit 0
    
            """.trimIndent())
        }
    }

    @Nested
    inner class Content {

        @Test
        fun `should provide content`() {
            expectThat(shellScript(null).content).matchesCurlyPattern("""
                #!/bin/sh
                {{}}
                'echo' 'Hello World!'
                'echo' 'Bye!'
                'exit' '42'
            """.trimIndent())
        }
    }

    @Nested
    inner class UsingScriptContext {

        @TestFactory
        fun `should echo`() = testEach<Pair<String, ScriptInit>>(
            "'echo'" to { echo() },
            "'echo' 'Hello!'" to { echo("Hello!") },
            "'echo' 'Hello World!'" to { echo("Hello World!") },
            "'echo' 'Hello' 'World!'" to { echo("Hello", "World!") },
        ) { (expected, init) ->
            expecting { ShellScript { init() } } that { string.lines().first().isEqualTo(expected) }
        }

        @Nested
        inner class FileOperations {

            @Test
            fun `should provide file operations by string`() {
                expectThat(ShellScript {
                    file("file.txt") {
                        appendLine("content")
                    }
                }).toStringMatchesCurlyPattern("""
                    cat <<HERE-{} >>"file.txt"
                    content
                    HERE-{}
    
                """.trimIndent())
            }

            @Test
            fun `should provide file operations by path`() {
                expectThat(ShellScript {
                    file("file.txt".asPath()) {
                        appendLine("content")
                    }
                }).toStringMatchesCurlyPattern("""
                    cat <<HERE-{} >>"file.txt"
                    content
                    HERE-{}
    
                """.trimIndent())
            }
        }

        @Nested
        inner class Embed {

            private fun getEmbeddedShellScript() = ShellScript("embedded script 📝") {
                shebang
                !"mkdir 'dir'"
                !"cd 'dir'"
                !"sleep 1"
                !"echo 'test' > 'file.txt'"
            }

            private fun ScriptContext.shellScript(): String {
                shebang
                !"echo 'about to run embedded script'"
                embed(getEmbeddedShellScript(), true)
                !"echo 'finished to run embedded script'"
                !"echo $(pwd)"
                return ""
            }

            @Test
            fun `should embed shell script`(uniqueId: UniqueId) = withTempDir(uniqueId) {
                expectThat(ShellScript { shellScript() }).toStringMatchesCurlyPattern("""
                #!/bin/sh
                echo 'about to run embedded script'
                (
                cat <<'EMBEDDED-SCRIPT-{}'
                #!/bin/sh
                echo '{}'
                mkdir 'dir'
                cd 'dir'
                sleep 1
                echo 'test' > 'file.txt'
                EMBEDDED-SCRIPT-{}
                ) > "./embedded-script-_.sh"
                if [ -f "./embedded-script-_.sh" ]; then
                  chmod 755 "./embedded-script-_.sh"
                  "./embedded-script-_.sh"
                  wait
                  rm "./embedded-script-_.sh"
                else
                  echo "Error creating ""embedded-script-_.sh"
                fi
                echo 'finished to run embedded script'
                echo $(pwd)
            """.trimIndent())
            }

            @Smoke @Test
            fun `should preserve functionality`(uniqueId: UniqueId) = withTempDir(uniqueId) {
                val exec = ShellScript {
                    changeDirectoryOrExit(this@withTempDir)
                    shellScript()
                }.exec.logging()

                expect {
                    that(exec.exitCodeOrNull).isEqualTo(0)
                    that(exec.io.ansiRemoved.lines().filter { "terminated successfully at" !in it }.joinLinesToString())
                        .matchesCurlyPattern("""
                        about to run embedded script
                        ░░░░░░░ EMBEDDED SCRIPT 📝
                        finished to run embedded script
                        $pathString
                    """.trimIndent())
                    that(resolve("dir/file.txt")) {
                        exists()
                        hasContent("test$LF")
                    }
                }
            }
        }

        @Nested
        inner class DockerCommand {

            @Test
            fun `should build valid docker run`() {
                expectThat(ShellScript {
                    shebang
                    !DockerRunCommandLine(
                        image = DockerImage { "image" / "name" },
                        options = Options(
                            name = DockerContainer.from("container-name"),
                            mounts = MountOptions {
                                Path.of("/a/b") mountAt "/c/d"
                                Path.of("/e/f/../g") mountAt "//h"
                            },
                        ),
                        executable = CommandLine("-arg1", "--argument", "2"),
                    )
                }).toStringIsEqualTo("""
                    #!/bin/sh
                    'docker' 'run' '--name' 'container-name' '--rm' '--interactive' '--mount' 'type=bind,source=/a/b,target=/c/d' '--mount' 'type=bind,source=/e/f/../g,target=/h' 'image/name' '-arg1' '--argument' '2'
                    
                """.trimIndent())
            }

            @Test
            fun `should build valid docker stop`() {
                expectThat(ShellScript {
                    shebang
                    !DockerStopCommandLine("busybox", "guestfish", time = 42.seconds)
                }).toStringIsEqualTo("""
                    #!/bin/sh
                    'docker' 'stop' '--time' '42' 'busybox' 'guestfish'
        
                """.trimIndent())
            }
        }

        @Test
        fun `should build comments`() {
            val sh = ShellScript {
                comment("test")
                "exit 0"
            }
            expectThat(sh).containsExactly("# test", "exit 0")
        }

        @Test
        fun `should build multi-line comments`() {
            expectThat(ShellScript {

                comment("""
                line 1
                line 2
            """.trimIndent())
                "exit 0"

            }).containsExactly("# line 1", "# line 2", "exit 0")
        }

        @Nested
        inner class Sudo {

            @Test
            fun `should create sudo line`(uniqueId: UniqueId) = withTempDir(uniqueId) {
                expectThat(ShellScript {
                    sudo("a password", "a command")
                }).get { last() }
                    .isEqualTo("echo \"a password\" | sudo -S a command")
            }
        }

        @Nested
        inner class DeleteOnCompletion {

            @Test
            fun `should create rm line`(uniqueId: UniqueId) = withTempDir(uniqueId) {
                expectThat(ShellScript {
                    deleteSelf()
                }).get { last() }
                    .isEqualTo("rm -- \"\$0\"")
            }

            @Test
            fun `should not remove itself by default`(uniqueId: UniqueId) = withTempDir(uniqueId) {
                val script = ShellScript().toFile(resolve("script.sh"))
                ShellScript { !script.pathString }.exec.logging()
                expectThat(resolve("script.sh")).exists()
            }

            @Test
            fun `should remove itself`(uniqueId: UniqueId) = withTempDir(uniqueId) {
                val script = ShellScript { deleteSelf() }.toFile(resolve("script.sh"))
                ShellScript { !script.pathString }.exec.logging()
                expectThat(resolve("script.sh")).not { exists() }
            }
        }
    }

    @Nested
    inner class CompanionObject {

        @TestFactory
        fun `should check if is script`(uniqueId: UniqueId) = tests {
            withTempDir(uniqueId) {
                expecting { "#!".toByteArray() } that { isScript() }
                expecting { "#".toByteArray() } that { not { isScript() } }
                expecting { "foo".toByteArray() } that { not { isScript() } }

                expecting { "#!" } that { isScript() }
                expecting { "#" } that { not { isScript() } }
                expecting { "foo" } that { not { isScript() } }

                expecting { randomFile().writeBytes("#!".toByteArray()) } that { isScript() }
                expecting { randomFile().writeBytes("#".toByteArray()) } that { not { isScript() } }
                expecting { randomFile().writeBytes("foo".toByteArray()) } that { not { isScript() } }
                expecting { resolve("does-not-exist") } that { not { isScript() } }
            }
        }
    }
}

fun Builder<ByteArray>.isScript(): Builder<ByteArray> =
    assert("is script") {
        if (it.isScript) pass()
        else fail("starts with ${it.take(2)}")
    }

@JvmName("charSequenceIsScript")
inline fun <reified T : CharSequence> Builder<T>.isScript() =
    assert("is script") {
        if (it.isScript) pass()
        else fail("starts with ${it.take(2)}")
    }

@JvmName("fileIsScript")
inline fun <reified T : Path> Builder<T>.isScript() =
    assert("is script") {
        if (it.isScript) pass()
        else if (!it.exists()) fail("does not exist")
        else fail("starts with ${it.inputStream().readNBytes(2)}")
    }

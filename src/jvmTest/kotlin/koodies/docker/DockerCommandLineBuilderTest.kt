package koodies.docker

import koodies.docker.DockerCommandLineTest.Companion.DOCKER_RUN_COMMAND
import koodies.shell.HereDocBuilder.hereDoc
import koodies.test.toStringIsEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

@Execution(CONCURRENT)
class DockerCommandLineBuilderTest {

    private val dockerImage = DockerImageBuilder.build { "repo" / "name" tag "tag" }

    @Test
    fun `should build valid docker run`() {
        val dockerRunCommand = DockerCommandLine.build(dockerImage) {
            options {
                name { "container-name" }
                privileged { true }
                autoCleanup { true }
                interactive { true }
                pseudoTerminal { true }
                mounts {
                    "/a/b".asHostPath() mountAt "/c/d".asContainerPath()
                    +MountOption("bind", "/e/f/../g".asHostPath(), "//h".asContainerPath())
                }
            }
            commandLine("work") {
                redirects {}
                environment {
                    "key1" to "value1"
                    "KEY2" to "VALUE 2"
                }
                workingDirectory { "/some/where".asHostPath() }

                arguments {
                    +"-arg1"
                    +"--argument" + "2"
                    +hereDoc(label = "HEREDOC") {
                        +"heredoc 1"
                        +"-heredoc-line-2"
                    }
                    +"/a/b/c" + "/c/d/e" + "/e/f/../g/h" + "/e/g/h" + "/h/i"
                    +"arg=/a/b/c" + "arg=/c/d/e" + "arg=/e/f/../g/h" + "arg=/e/g/h" + "arg=/h/i"
                }
            }
        }
        expectThat(dockerRunCommand).toStringIsEqualTo(DOCKER_RUN_COMMAND.toString())
    }

    @Test
    fun `should build same format for no sub builders and empty sub builders`() {
        val commandBuiltWithNoBuilders = DockerCommandLine.build(dockerImage)
        val commandBuiltWithEmptyBuilders = DockerCommandLine.build(dockerImage) {
            options { }
            commandLine { }
        }

        expectThat(commandBuiltWithNoBuilders).isEqualTo(commandBuiltWithEmptyBuilders)
    }

    @Test
    fun `should set auto cleanup as default`() {
        expectThat(DockerCommandLine.build(dockerImage).arguments).contains("--rm")
    }

    @Test
    fun `should set interactive as default`() {
        expectThat(DockerCommandLine.build(dockerImage).arguments).contains("-i")
    }
}
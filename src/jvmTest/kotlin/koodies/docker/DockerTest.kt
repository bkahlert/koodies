package koodies.docker

import koodies.docker.TestImages.BusyBox
import koodies.docker.TestImages.Ubuntu
import koodies.exec.Exec
import koodies.exec.ansiKept
import koodies.exec.ansiRemoved
import koodies.exec.io
import koodies.exec.output
import koodies.io.compress.Archiver.unarchive
import koodies.io.path.asPath
import koodies.io.path.getSize
import koodies.io.path.listDirectoryEntriesRecursively
import koodies.logging.InMemoryLogger
import koodies.test.HtmlFile
import koodies.test.IdeaWorkaroundTest
import koodies.test.Smoke
import koodies.test.SvgFile
import koodies.test.UniqueId
import koodies.test.asserting
import koodies.test.copyTo
import koodies.test.testEach
import koodies.test.withTempDir
import koodies.text.containsEscapeSequences
import koodies.text.toStringMatchesCurlyPattern
import koodies.unit.bytes
import koodies.unit.hasSize
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.fail
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.any
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isGreaterThan
import strikt.assertions.isNull
import strikt.assertions.isSuccess
import strikt.assertions.isTrue
import strikt.assertions.length
import strikt.java.exists
import strikt.java.fileName
import java.net.URI
import java.nio.file.Path

class DockerTest {

    @Nested
    inner class InfoProperty {

        @DockerRequiring @Test
        fun `should return info for existing key`() {
            expectThat(Docker.info["server.server-version"]).toStringMatchesCurlyPattern("{}.{}.{}")
        }

        @DockerRequiring @Test
        fun `should return null for unknown key`() {
            expectThat(Docker.info["unknown.key"]).isNull()
        }
    }

    @Nested
    inner class ImageProperty {

        @Test
        fun `should build instances`() {
            expectThat(Docker.images { "hello-world" }).isEqualTo(TestImages.HelloWorld)
        }

        @Test
        fun `should provide commands`() {
            expectCatching { Docker.images.list() }.isSuccess()
        }
    }

    @Nested
    inner class ContainerProperty {

        @Test
        fun `should build instances`() {
            expectThat(Docker.containers { "docker-container".sanitized }).isEqualTo(DockerContainer("docker-container"))
        }

        @DockerRequiring @Test
        fun `should provide commands`() {
            expectCatching { Docker.containers.list() }.isSuccess()
        }
    }

    @DockerRequiring @Test
    fun `should return true if engine is running`() {
        expectThat(Docker.engineRunning).isTrue()
    }

    @Nested
    inner class ContainerRunning {

        @ContainersTest @IdeaWorkaroundTest
        fun `should return true if container is running`(testContainers: TestContainers) {
            val container = testContainers.newRunningTestContainer()
            expectThat(Docker(container.name).isRunning).isTrue()
        }

        @ContainersTest @IdeaWorkaroundTest
        fun `should return false if container exited`(testContainers: TestContainers) {
            val container = testContainers.newExitedTestContainer()
            expectThat(Docker(container.name).isRunning).isFalse()
        }

        @ContainersTest @IdeaWorkaroundTest
        fun `should return false if container does not exist`(testContainers: TestContainers) {
            val container = testContainers.newNotExistentContainer()
            expectThat(Docker(container.name).isRunning).isFalse()
        }
    }

    @DockerRequiring @Test
    fun `should search`(uniqueId: UniqueId) = withTempDir(uniqueId) {
        expectThat(Docker.search("busybox")).any { image.isEqualTo(BusyBox) }
    }

    @Suppress("SpellCheckingInspection")
    object LibRSvg : DockerImage("minidocks", listOf("librsvg"))

    @Suppress("SpellCheckingInspection")
    object Chafa : DockerImage("rafib", listOf("awesome-cli-binaries"))

    @Nested
    inner class Shortcuts {

        /**
         * Creates a temporary directory with an HTML file inside.
         * @param uniqueId used to make guarantee an isolated directory
         * @param block run with the temporary directory as the receiver and the name of the HTML file as its argument
         */
        private fun withHtmlFile(uniqueId: UniqueId, block: Path.(String) -> Unit) {
            withTempDir(uniqueId) {
                HtmlFile.copyTo(resolve("index.html"))
                block("index.html")
            }
        }

        @DockerRequiring([Ubuntu::class, BusyBox::class]) @TestFactory
        fun `should run using well-known images`(uniqueId: UniqueId) = testEach<Path.(String) -> Exec>(
            { fileName -> ubuntu("cat", fileName) },
            { fileName -> ubuntu { "cat $fileName" } },
            { fileName -> busybox("cat", fileName) },
            { fileName -> busybox { "cat $fileName" } },
        ) { exec ->
            withHtmlFile(uniqueId) { name ->
                expecting { exec(name) } that { io.output.ansiRemoved.isEqualTo(HtmlFile.text) }
            }
        }

        @DockerRequiring([Ubuntu::class]) @TestFactory
        fun `should run command line`(uniqueId: UniqueId) = testEach<Path.(String, String) -> Exec>(
            { command, args -> docker("ubuntu", command, args) },
            { command, args -> docker({ "ubuntu" }, command, args) },
            { command, args -> docker(Ubuntu, command, args) },
        ) { exec ->
            withHtmlFile(uniqueId) { name ->
                HtmlFile.copyTo(resolve(name))
                expecting { exec("cat", name) } that { io.output.ansiRemoved.isEqualTo(HtmlFile.text) }
            }
        }

        @DockerRequiring([Ubuntu::class]) @TestFactory
        fun `should run shell script`(uniqueId: UniqueId) = testEach<Path.(ScriptInitWithWorkingDirectory) -> Exec>(
            { scriptInit -> docker("busybox") { wd -> scriptInit(wd) } },
            { scriptInit -> docker({ "busybox" }) { wd -> scriptInit(wd) } },
            { scriptInit -> docker(BusyBox) { wd -> scriptInit(wd) } },
        ) { exec ->
            withHtmlFile(uniqueId) { name ->
                HtmlFile.copyTo(resolve(name))
                expecting { exec { "cat $name" } } that { io.output.ansiRemoved.isEqualTo(HtmlFile.text) }
            }
        }

        @Nested
        inner class Download {

            private val uri = "https://github.com/NicolasCARPi/example-files/raw/master/example.png"

            @DockerRequiring @TestFactory
            fun `should download`(uniqueId: UniqueId, logger: InMemoryLogger) = testEach<Path.(String) -> Path>(
                { download(it, logger = logger) },
                { download(URI.create(it), logger = logger) },
            ) { download -> withTempDir(uniqueId) { expecting { download(uri) } that { hasSize(40959.bytes) } } }

            @DockerRequiring @TestFactory
            fun `should download use given name`(uniqueId: UniqueId, logger: InMemoryLogger) = testEach<Path.(String, String) -> Path>(
                { uri, name -> download(uri, name, logger = logger) },
                { uri, name -> download(URI.create(uri), name, logger = logger) },
            ) { download -> withTempDir(uniqueId) { expecting { download(uri, "custom.png") } that { fileName.isEqualTo("custom.png".asPath()) } } }

            @DockerRequiring @TestFactory
            fun `should download use remote name`(uniqueId: UniqueId, logger: InMemoryLogger) = testEach<Path.(String) -> Path>(
                { download(it, logger = logger) },
                { download(URI.create(it), logger = logger) },
            ) { download -> withTempDir(uniqueId) { expecting { download(uri) } that { fileName.isEqualTo("example.png".asPath()) } } }

            @DockerRequiring @TestFactory
            fun `should download clean remote name`(uniqueId: UniqueId, logger: InMemoryLogger) = testEach<Path.(String) -> Path>(
                { download(it, logger = logger) },
                { download(URI.create(it), logger = logger) },
            ) { download -> withTempDir(uniqueId) { expecting { download("$uri?a=b#c") } that { fileName.isEqualTo("example.png".asPath()) } } }
        }

        @Suppress("SpellCheckingInspection")
        @DockerRequiring @Test
        fun InMemoryLogger.`should run dockerpi`(uniqueId: UniqueId) = withTempDir(uniqueId) {
            val uri = "https://www.riscosopen.org/zipfiles/platform/raspberry-pi/BCM2835Dev.5.29.zip?1604815147"
            val unarchive: Path = download(uri).unarchive()
            val listDirectoryEntriesRecursively: List<Path> = unarchive.listDirectoryEntriesRecursively()
            val maxOf = listDirectoryEntriesRecursively.maxByOrNull { it.getSize() } ?: fail { "No image found." }
            maxOf.dockerPi { io ->
                logLine { io }
            } asserting { io.output.ansiRemoved.contains("Rebooting in 1 seconds") }
        }

        @Suppress("SpellCheckingInspection")
        @DockerRequiring([LibRSvg::class, Chafa::class]) @Smoke @Test
        fun `should run multiple containers`(uniqueId: UniqueId, logger: InMemoryLogger) = withTempDir(uniqueId) {
            SvgFile.copyTo(resolve("koodies.svg"))

            docker(LibRSvg, "-z", 10, "--output", "koodies.png", "koodies.svg")
            resolve("koodies.png") asserting { exists() }

            docker(Chafa, logger) {
                """
               /opt/bin/chafa koodies.png 
                """
            } asserting {
                io.output.ansiKept
                    .containsEscapeSequences()
                    .length.isGreaterThan(1000)
            }
        }
    }
}

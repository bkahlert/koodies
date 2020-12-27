package koodies.io.compress

import koodies.io.compress.TarArchiveGzCompressor.listArchive
import koodies.io.compress.TarArchiveGzCompressor.tarGunzip
import koodies.io.compress.TarArchiveGzCompressor.tarGzip
import koodies.io.path.addExtensions
import koodies.io.path.copyTo
import koodies.io.path.hasSameFiles
import koodies.io.path.randomPath
import koodies.io.path.removeExtensions
import koodies.io.path.renameTo
import koodies.io.path.touch
import koodies.test.Fixtures.archiveWithTwoFiles
import koodies.test.Fixtures.directoryWithTwoFiles
import koodies.test.testWithTempDir
import koodies.test.withTempDir
import koodies.unit.Size.Companion.bytes
import koodies.unit.Size.Companion.size
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.exists
import strikt.assertions.isA
import strikt.assertions.isFailure
import strikt.assertions.isLessThan
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.writeText

@Execution(CONCURRENT)
class TarArchiveGzCompressorTest {

    @TestFactory
    fun `should throw on missing source`() = listOf<Path.() -> Path>(
        { randomPath().tarGzip() },
        { randomPath(extension = ".tar.gz").tarGunzip() },
    ).testWithTempDir { call ->
        expectCatching { call() }.isFailure().isA<NoSuchFileException>()
    }

    @TestFactory
    fun `should throw on non-empty destination`() = listOf<Path.() -> Path>(
        { directoryWithTwoFiles().apply { addExtensions("tar.gz").touch().writeText("content") }.tarGzip() },
        { archiveWithTwoFiles("tar.gz").apply { copyTo(removeExtensions("tar.gz")) }.tarGunzip() },
    ).testWithTempDir { call ->
        expectCatching { call() }.isFailure().isA<FileAlreadyExistsException>()
    }

    @TestFactory
    fun `should overwrite non-empty destination`() = listOf<Path.() -> Path>(
        { directoryWithTwoFiles().apply { addExtensions("tar.gz").touch().writeText("content") }.tarGzip(overwrite = true) },
        { archiveWithTwoFiles("tar.gz").apply { copyTo(removeExtensions("tar.gz")) }.tarGunzip(overwrite = true) },
    ).testWithTempDir { call ->
        expectThat(call()).exists()
    }

    @Test
    fun `should tar-gzip listArchive and untar-gunzip`() = withTempDir {
        val dir = directoryWithTwoFiles()

        val archivedDir = dir.tarGzip()
        expectThat(archivedDir.size).isLessThan(dir.size.coerceAtLeast(500.bytes))

        expectThat(archivedDir.listArchive().map { it.name }).containsExactlyInAnyOrder("example.html", "sub-dir/", "sub-dir/config.txt")

        val renamedDir = dir.renameTo("${dir.fileName}-renamed")

        val unarchivedDir = archivedDir.tarGunzip()
        expectThat(unarchivedDir).hasSameFiles(renamedDir)
    }
}

package koodies.io.compress

import koodies.io.compress.Archiver.archive
import koodies.io.compress.Archiver.listArchive
import koodies.io.compress.Archiver.unarchive
import koodies.io.compress.TarArchiveGzCompressor.tarGunzip
import koodies.io.compress.TarArchiveGzCompressor.tarGzip
import koodies.io.path.addExtensions
import koodies.io.path.copyTo
import koodies.io.path.getSize
import koodies.io.path.hasSameFiles
import koodies.io.path.removeExtensions
import koodies.io.path.renameTo
import koodies.io.path.touch
import koodies.io.path.writeText
import koodies.io.randomDirectory
import koodies.io.randomPath
import koodies.junit.UniqueId
import koodies.test.Fixtures.archiveWithTwoFiles
import koodies.test.Fixtures.directoryWithTwoFiles
import koodies.test.testEach
import koodies.test.withTempDir
import koodies.unit.bytes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.isLessThan
import strikt.java.exists
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class ArchiverTarGzTest {

    @TestFactory
    fun `should throw on missing source`(uniqueId: UniqueId) = testEach<Path.() -> Path>(
        { randomPath().archive() },
        { randomPath(extension = ".tar.gz").unarchive() },
        { randomPath(extension = ".tar.gz").apply { listArchive() } },
    ) { call ->
        withTempDir(uniqueId) {
            expectThrows<NoSuchFileException> { call() }
        }
    }

    @TestFactory
    fun `should throw on non-empty destination`(uniqueId: UniqueId) = testEach<Path.() -> Path>(
        { directoryWithTwoFiles().apply { addExtensions("tar").addExtensions("gz").touch().writeText("content") }.archive("tar.gz") },
        { archiveWithTwoFiles("tar.gz").apply { copyTo(removeExtensions("gz").removeExtensions("tar")) }.unarchive() },
    ) { call ->
        withTempDir(uniqueId) {
            expectThrows<FileAlreadyExistsException> { call() }
        }
    }

    @TestFactory
    fun `should overwrite non-empty destination`(uniqueId: UniqueId) = testEach<Path.() -> Path>(
        {
            randomDirectory().directoryWithTwoFiles().apply { addExtensions("tar").addExtensions("gz").touch().writeText("content") }
                .archive("tar.gz", overwrite = true)
        },
        { randomDirectory().archiveWithTwoFiles("tar.gz").apply { copyTo(removeExtensions("gz").removeExtensions("tar")) }.unarchive(overwrite = true) },
    ) { call ->
        withTempDir(uniqueId) {
            expectThat(call()).exists()
        }
    }

    @Test
    fun `should tar-gzip and untar-gunzip`(uniqueId: UniqueId) = withTempDir(uniqueId) {
        val dir = directoryWithTwoFiles()

        val archivedDir = dir.tarGzip()
        expectThat(archivedDir.getSize()).isLessThan(dir.getSize().coerceAtLeast(500.bytes))

        val renamedDir = dir.renameTo("${dir.fileName}-renamed")

        val unarchivedDir = archivedDir.tarGunzip()
        expectThat(unarchivedDir).hasSameFiles(renamedDir)
    }
}

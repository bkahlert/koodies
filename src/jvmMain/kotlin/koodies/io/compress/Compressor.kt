package koodies.io.compress

import koodies.io.file.bufferedInputStream
import koodies.io.file.outputStream
import koodies.io.path.addExtensions
import koodies.io.path.deleteRecursively
import koodies.io.path.extensionOrNull
import koodies.io.path.removeExtensions
import koodies.io.path.requireExists
import koodies.io.path.requireExistsNot
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.nio.file.Path

/**
 * Provides (de-)compression functionality for a range of compression algorithms.
 *
 * @see CompressorStreamFactory
 */
object Compressor {
    /**
     * Compresses this file using the provided compression algorithm.
     *
     * By default the existing file name is used and the appropriate extension (e.g. `.gz` or `.bzip2`) appended.
     */
    fun Path.compress(
        format: String = CompressorStreamFactory.BZIP2,
        destination: Path = addExtensions(format),
        overwrite: Boolean = false,
    ): Path {
        requireExists()
        if (overwrite) destination.deleteRecursively() else destination.requireExistsNot()
        bufferedInputStream().use { inputStream ->
            CompressorStreamFactory().createCompressorOutputStream(format, destination.outputStream()).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return destination
    }

    /**
     * Decompresses this compressed file.
     *
     * By default the existing file name is used with the extension removed.
     */
    fun Path.decompress(
        destination: Path = removeExtensions(extensionOrNull!!),
        overwrite: Boolean = false,
    ): Path {
        requireExists()
        if (overwrite) destination.deleteRecursively() else destination.requireExistsNot()
        CompressorStreamFactory().createCompressorInputStream(bufferedInputStream()).use { inputStream ->
            destination.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return destination
    }
}

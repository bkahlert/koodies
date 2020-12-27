package koodies.nio

import koodies.concurrent.process.IO.Type.META
import koodies.debug.debug
import koodies.logging.BlockRenderingLogger
import koodies.logging.MutedRenderingLogger
import koodies.logging.singleLineLogging
import koodies.text.withRandomSuffix
import org.apache.commons.io.output.ByteArrayOutputStream
import org.jline.utils.NonBlocking
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset
import kotlin.time.Duration
import kotlin.time.seconds
import org.jline.utils.NonBlockingReader as JLineNonBlockingReader

/**
 * Reads a [ByteArrayOutputStream] line-wise using a special rule:
 * If no complete line is available the currently available content is returned.
 * This happens all over again until that line is complete, that is, terminated
 * by `\r\n` or `\n`.
 */
class NonBlockingCharReader(
    private val inputStream: InputStream,
    private val timeout: Duration = 6.seconds,
    private val charset: Charset = Charsets.UTF_8,
    name: String = "ImgCstmzr-${NonBlockingCharReader::class.simpleName}".withRandomSuffix(),
) : Reader() {

    private val timeoutMillis: Long = timeout.toLongMilliseconds()
    private inline val inlineTimeoutMillis get() = timeoutMillis

    var reader: JLineNonBlockingReader? = NonBlocking.nonBlocking(name, inputStream, charset)

    fun read(buffer: CharArray, off: Int, logger: BlockRenderingLogger): Int = if (reader == null) -1 else
        logger.singleLineLogging(NonBlockingCharReader::class.simpleName + ".read(CharArray, Int, Int, Logger)") {
            when (val read = kotlin.runCatching { reader?.read(inlineTimeoutMillis) ?: throw IOException("No reader. Likely already closed.") }
                .recover {
                    reader?.close()
                    -1
                }.getOrThrow()) {
                -1 -> {
                    logStatus { META typed "EOF" }
                    -1
                }
                -2 -> {
                    logStatus { META typed "TIMEOUT" }
                    0
                }
                else -> {
                    logStatus { META typed "SUCCESSFULLY READ ${read.debug}" }
                    buffer[off] = read.toChar()
                    1
                }
            }
        }

    override fun read(cbuf: CharArray, off: Int, len: Int): Int = read(cbuf, off, MutedRenderingLogger())

    override fun close() {
        kotlin.runCatching { reader?.close() }
        reader = null
    }

    override fun toString(): String = "NonBlockingCharReader(inputStream=$inputStream, timeout=$timeout, charset=$charset, reader=$reader)"
}
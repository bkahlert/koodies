package koodies.io

import koodies.asString
import koodies.collections.headOrNull
import koodies.collections.tail
import java.io.OutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public open class TeeOutputStream(
    private val output: OutputStream,
    private vararg val branches: OutputStream,
) : ProxyOutputStream(output) {

    public constructor(outputStreams: List<OutputStream>) : this(outputStreams.headOrNull ?: nullOutputStream(), *outputStreams.tail.toTypedArray())

    private val lock = ReentrantLock()

    protected fun each(block: (OutputStream).() -> Unit): Unit =
        branches.forEach { it.block() }

    override fun write(bytes: ByteArray): Unit = lock.withLock {
        super.write(bytes)
        each { write(bytes) }
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int): Unit = lock.withLock {
        super.write(bytes, offset, length)
        each { write(bytes, offset, length) }
    }

    override fun write(byte: Int): Unit = lock.withLock {
        super.write(byte)
        each { write(byte) }
    }

    override fun flush(): Unit = lock.withLock {
        super.flush()
        each { flush() }
    }

    override fun close(): Unit = lock.withLock {
        try {
            super.close()
        } finally {
            each { runCatching { close() } }
        }
    }

    override fun toString(): String = asString {
        ::output to output
        ::branches to branches.toList()
    }
}

package koodies.test.output

import koodies.concurrent.process.IO
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A capture session that captures [System.out] and [System.err].
 */
open class SystemCapture {
    private val lock = ReentrantLock()
    private val out: PrintStreamCapture
    private val err: PrintStreamCapture

    private val capturedStrings: MutableList<IO> = arrayListOf()

    init {
        out = PrintStreamCapture(System.out) { string: String -> captureOut(string) }
        err = PrintStreamCapture(System.err) { string: String -> captureErr(string) }
        System.setOut(out)
        System.setErr(err)
    }

    fun release() {
        System.setOut(out.parent)
        System.setErr(err.parent)
    }

    private fun captureOut(string: String) {
        lock.withLock { capturedStrings.add(IO.OUT typed string) }
    }

    private fun captureErr(string: String) {
        lock.withLock { capturedStrings.add(IO.ERR typed string) }
    }

    public fun useCapturedStrings(transform: (IO) -> Unit) {
        lock.withLock {
            capturedStrings.forEach { transform(it) }
        }
    }

    fun reset() {
        lock.withLock { capturedStrings.clear() }
    }
}

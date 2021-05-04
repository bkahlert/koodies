package koodies.exec.mock

import koodies.debug.asEmoji
import koodies.exec.CommandLine
import koodies.exec.Exec
import koodies.exec.JavaExec
import koodies.io.path.Locations
import koodies.text.Semantics.Symbols

/**
 * [Exec] mock to ease testing.
 */
public open class ExecMock(
    private val javaProcessMock: JavaProcessMock,
    private val name: String? = null,
) : Exec by JavaExec(javaProcessMock, Locations.Temp, CommandLine("echo", ExecMock::class.simpleName!!)) {

    override fun toString(): String {
        val delegateString = "${javaProcessMock.toString().replaceFirst('[', '(').dropLast(1) + ")"}, successful=${successful?.asEmoji ?: Symbols.Computation})"
        val string = "${ExecMock::class.simpleName ?: "object"}(delegate=$delegateString)".substringBeforeLast(")")
        return string.takeUnless { name != null } ?: string.substringBeforeLast(")") + ", name=$name)"
    }

    public companion object {
        public val RUNNING_EXEC: ExecMock get() = ExecMock(JavaProcessMock.RUNNING_PROCESS)
        public val SUCCEEDED_EXEC: ExecMock
            get() = ExecMock(JavaProcessMock.SUCCEEDING_PROCESS).apply {
                outputStream.readBytes()
                onExit.join()
            }
        public val FAILED_EXEC: ExecMock
            get() = ExecMock(JavaProcessMock.FAILING_PROCESS).apply {
                errorStream.readBytes()
                onExit.join()
            }
    }
}

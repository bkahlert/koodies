package koodies.shell

import koodies.concurrent.process.CommandLine
import koodies.io.path.executable
import koodies.io.path.withDirectoriesCreated
import koodies.io.path.writeText
import koodies.terminal.Banner.banner
import koodies.text.LineSeparators.LF
import koodies.text.LineSeparators.lines
import koodies.text.LineSeparators.withoutTrailingLineSeparator
import koodies.text.prefixLinesWith
import koodies.text.quoted
import koodies.text.withRandomSuffix
import koodies.toBaseName
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.notExists

@DslMarker
annotation class ShellScriptMarker

@ShellScriptMarker
class ShellScript(val name: String? = null, content: String? = null) {

    val lines: MutableList<String> = mutableListOf()

    init {
        if (content != null) lines.addAll(content.trimIndent().lines())
    }

    fun changeDirectoryOrExit(directory: Path, @Suppress("UNUSED_PARAMETER") errorCode: Int = -1) {
        lines.add("cd \"$directory\" || exit -1")
    }

    operator fun String.not() {
        lines.add(this)
    }

    /**
     * Builds a script line based on [words]. All words are combined using a single space.
     */
    fun line(vararg words: String) {
        lines.add(words.joinToString(" "))
    }

    /**
     * Builds a script [line] based on a single string already making up that script.
     */
    fun line(line: String) {
        lines.add(line)
    }

    /**
     * Builds a [command] call using the [arguments].
     */
    fun command(command: String, vararg arguments: String) {
        lines.add(listOf(command, *arguments).joinToString(" "))
    }

    /**
     * Builds a [command] call.
     */
    fun command(command: CommandLine) {
        lines.addAll(command.lines)
    }

    /**
     * Initializes a [FileOperations] builder for the file specified by [path] and
     * the optional [init] applied to it.
     */
    fun file(path: String, init: FileOperations.() -> Unit = {}) = FileOperations(this, path).apply(init)

    /**
     * Initializes a [FileOperations] builder for the file specified by [path] and
     * the optional [init] applied to it.
     */
    fun file(path: Path, init: FileOperations.() -> Unit = {}) = FileOperations(this, path).apply(init)

    fun embed(shellScript: ShellScript) {
        val fileName = "${shellScript.name.toBaseName()}.sh"
        val delimiter = "EMBEDDED-SCRIPT".withRandomSuffix()
        lines.add("""
            (
            cat <<'$delimiter'
        """.trimIndent())
        lines.add(shellScript.build().withoutTrailingLineSeparator)
        lines.add("""
            $delimiter
            ) > "$fileName"
            if [ -f "$fileName" ]; then
              chmod 755 "$fileName"
              "./$fileName"
              wait
              rm "$fileName"
            else
              echo "Error creating \"$fileName\""
            fi
        """.trimIndent())
    }

    fun exit(code: Int) {
        lines.add("exit $code")
    }

    fun comment(text: String) {
        lines += text.prefixLinesWith(prefix = "# ")
    }

    fun sudo(password: String, command: String) {
        lines += "echo ${password.quoted} | sudo -S $command"
    }

    fun deleteOnCompletion() {
        lines += "rm -- \"\$0\""
    }

    fun sanitize(workingDirectory: Path? = null): ShellScript {
        var linesKept = lines.dropWhile { it.isShebang() || it.isBlank() }
        if (workingDirectory != null && linesKept.firstOrNull()?.startsWith("cd ") == true) linesKept = linesKept.drop(1)
        return ShellScript(name = name).apply {
            shebang
            workingDirectory?.let { changeDirectoryOrExit(it) }
            linesKept.filter { !it.isShebang() }.forEach { lines += it }
        }
    }

    fun build(name: String? = this.name): String {
        var echoNameCommandAdded = false
        val echoNameCommand = bannerEchoingCommand(name)
        val script = lines.joinToString("") { line ->
            if (!echoNameCommandAdded && line.isShebang()) {
                echoNameCommandAdded = true
                line + LF + echoNameCommand
            } else {
                line + LF
            }
        }
        return if (echoNameCommandAdded) script else echoNameCommand + script
    }

    fun buildTo(path: Path): Path = path.apply {
        if (path.notExists()) path.withDirectoriesCreated().createFile()
        writeText(build())
        executable = true
    }

    override fun toString(): String = "Script(name=$name;content=${build().lines(ignoreTrailingSeparator = true).joinToString(";")}})"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShellScript

        if (name != other.name) return false
        if (lines != other.lines) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + lines.hashCode()
        return result
    }

    companion object {
        /**
         * Builds and returns an actual instance.
         */
        fun (ShellScript.() -> Unit).build(name: String? = null): ShellScript =
            ShellScript(name = name).apply(this)

        operator fun invoke(name: String? = null, block: ShellScript.() -> Unit): ShellScript {
            val build = block.build()
            val content = build.build()
            return ShellScript(name, content)
        }

        /**
         * Return—if [name] is not `null`—a command line that echos [name].
         *
         * If [name] is `null` an empty string is returned.
         */
        fun bannerEchoingCommand(name: String?): String = name?.let { "echo ${banner(name).quoted}$LF" } ?: ""
    }
}

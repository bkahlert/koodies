package koodies.exception

import koodies.io.file.writeText
import koodies.io.path.randomFile
import koodies.io.path.withExtension
import koodies.runtime.deleteOldTempFilesOnExit
import koodies.terminal.AnsiCode.Companion.removeEscapeSequences
import koodies.text.LineSeparators.LF
import koodies.text.joinLinesToString
import koodies.text.withSuffix
import java.io.IOException
import java.nio.file.Path
import kotlin.time.days

private object Dump {
    const val dumpPrefix = "koodies.dump."
    const val dumpSuffix = ".log"

    init {
        deleteOldTempFilesOnExit(dumpPrefix, dumpSuffix, 5.days, keepAtMost = 200)
    }
}

@Suppress("unused")
private val dumpInitWorkaround = "$Dump"

/**
 * Dumps whatever is returned by [data] to the specified [file] in `this` directory and
 * returns a description of the dump.
 *
 * If an error occurs in this process—so to as a last resort—the returned description
 * includes the complete dump itself.
 */
fun Path.dump(errorMessage: String?, file: Path = randomFile(Dump.dumpPrefix, Dump.dumpSuffix), data: () -> String): String = runCatching {
    var dumped: String? = null
    val dumps = persistDump(file) { data().also { dumped = it } }

    val dumpedLines = (dumped ?: error("Dump seems empty")).lines()
    val recentLineCount = dumpedLines.size.coerceAtMost(10)

    (errorMessage?.withSuffix(LF)?.capitalize() ?: "") +
        "➜ A dump has been written to:$LF" +
        dumps.entries.joinLinesToString(postfix = LF) { "  - ${it.value.toUri()} (${it.key})" } +
        "➜ The last $recentLineCount lines are:$LF" +
        dumpedLines.takeLast(recentLineCount).map { "  $it" }.joinLinesToString(postfix = LF)
}.recover { ex: Throwable ->
    (errorMessage?.withSuffix(LF)?.capitalize() ?: "") +
        "In the attempt to persist the corresponding dump the following error occurred:$LF" +
        "${ex.toSingleLineString()}$LF" +
        LF +
        "➜ The not successfully persisted dump is as follows:$LF" +
        data()
}.getOrThrow()

/**
 * Dumps whatever is returned by [data] to the specified [path] and
 * returns a description of the dump.
 *
 * If an error occurs in this process—so to as as a last resort—the returned description
 * includes the complete dump itself.
 */
@Suppress("unused")
fun Path.dump(errorMessage: String?, path: Path = randomFile(Dump.dumpPrefix, Dump.dumpSuffix), data: String): String =
    dump(errorMessage, path) { data }

/**
 * Dumps whatever is returned by [data] to the specified [path].
 *
 * This method returns a map of file format to [Path] mappings.
 */
fun persistDump(path: Path, data: () -> String): Map<String, Path> = runCatching {
    data().run {
        mapOf("unchanged" to path.withExtension("log").writeText(this),
            "ANSI escape/control sequences removed" to path.withExtension("no-ansi.log").writeText(removeEscapeSequences()))
    }
}.getOrElse {
    if (it is IOException) throw it
    throw IOException(it)
}
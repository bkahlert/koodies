package koodies.exception

import koodies.Koodies
import koodies.io.Locations
import koodies.io.path.isSubPathOf
import koodies.io.path.withExtension
import koodies.io.path.writeText
import koodies.io.randomFile
import koodies.text.ANSI.ansiRemoved
import koodies.text.LineSeparators.LF
import koodies.text.capitalize
import koodies.text.withSuffix
import java.io.IOException
import java.nio.file.Path

private object Dump {
    val dumpDir: Path = Koodies.ExecTemp
    const val dumpPrefix = "dump"
    const val dumpSuffix = ".log"
}

/**
 * Dumps whatever is returned by [data] to the specified [file] in `this` directory and
 * returns a description of the dump.
 *
 * If an error occurs in this process—so to as a last resort—the returned description
 * includes the complete dump itself.
 */
public fun Path.dump(
    errorMessage: String?,
    file: Path = (takeUnless { it.isSubPathOf(Locations.Temp) } ?: Dump.dumpDir).randomFile(Dump.dumpPrefix, Dump.dumpSuffix),
    data: () -> String,
): String = runCatching {
    var dumped: String? = null
    val dumps = persistDump(file) { data().also { dumped = it } }

    val dumpedLines = (dumped ?: error("Dump seems empty")).lines()
    val recentLineCount = dumpedLines.size.coerceAtMost(10)

    (errorMessage?.withSuffix(LF)?.capitalize() ?: "") +
        "➜ A dump has been written to:$LF" +
        dumps.entries.joinToString("") { "  - ${it.value.toUri()} (${it.key})$LF" } +
        "➜ The last $recentLineCount lines are:$LF" +
        dumpedLines.takeLast(recentLineCount).joinToString("") { "  $it$LF" }
}.recover { ex: Throwable ->
    (errorMessage?.withSuffix(LF)?.capitalize() ?: "") +
        "In the attempt to persist the corresponding dump the following error occurred:$LF" +
        "${ex.toCompactString()}$LF" +
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
public fun Path.dump(
    errorMessage: String?,
    path: Path = randomFile(Dump.dumpPrefix, Dump.dumpSuffix),
    data: String,
): String =
    dump(errorMessage, path) { data }

/**
 * Dumps whatever is returned by [data] to the specified [path].
 *
 * This method returns a map of file format to [Path] mappings.
 *
 * ***Note:** To also create an default error message, use [Path.dump].
 */
public fun persistDump(
    path: Path,
    data: () -> String,
): Map<String, Path> = runCatching {
    data().run {
        mapOf("unchanged" to path.withExtension("log").writeText(this),
            "ANSI escape/control sequences removed" to path.withExtension("ansi-removed.log").writeText(ansiRemoved))
    }
}.getOrElse {
    if (it is IOException) throw it
    throw IOException(it)
}

package koodies.exception

import koodies.concurrent.process.Process
import koodies.debug.replaceNonPrintableCharacters
import koodies.text.LineSeparators
import koodies.text.LineSeparators.lines
import koodies.text.withoutSuffix
import java.nio.file.Path

fun Any?.toCompactString(): String = when (this) {
    is Path -> toUri().toString()
    is Array<*> -> toList().toCompactString()
    is Iterable<*> -> joinToString(prefix = "[", postfix = "]") { it.toCompactString() }
    is Process -> also { waitFor() }.exitValue.toString()
    is java.lang.Process -> also { waitFor() }.exitValue().toString()
    else -> when (this) {
        null -> ""
        Unit -> ""
        else -> {
            val string = toString()
            if (string in LineSeparators) string.replaceNonPrintableCharacters()
            else string.lines().joinToString(separator = "⏎").withoutSuffix("⏎")
        }
    }
}

fun Throwable?.toCompactString(): String {
    if (this == null) return ""
    val messagePart = message?.let { ": " + it.lines()[0] } ?: ""
    return rootCause.run {
        this::class.simpleName + messagePart + stackTrace?.firstOrNull()
            ?.let { element -> " at.(${element.fileName}:${element.lineNumber})" }
    }
}

fun Result<*>?.toCompactString(): String {
    if (this == null) return ""
    return if (isSuccess) getOrNull().toCompactString()
    else exceptionOrNull().toCompactString()
}

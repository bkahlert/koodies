package koodies.exception

import koodies.debug.replaceNonPrintableCharacters
import koodies.exec.Process
import koodies.math.toHexadecimalString
import koodies.text.LineSeparators
import koodies.text.LineSeparators.lines
import java.nio.file.Path

// TODO merge with simpleString
public fun Any?.toCompactString(): String = when (this) {
    is Path -> toUri().toString()
    is ByteArray -> toHexadecimalString()
    is Array<*> -> toList().toCompactString()
    is Iterable<*> -> joinToString(prefix = "[", postfix = "]") { it.toCompactString() }
    is Process -> kotlin.runCatching { state.status }.fold({ it }, { it.toCompactString() })
    is java.lang.Process -> also { waitFor() }.exitValue().toString()
    else -> when (this) {
        null -> ""
        Unit -> ""
        else -> {
            val string = toString()
            if (string in LineSeparators) string.replaceNonPrintableCharacters()
            else string.lines().joinToString(separator = "⏎").removeSuffix("⏎")
        }
    }
}

public fun Throwable?.toCompactString(): String {
    if (this == null) return ""
    val messagePart = message?.let { ": " + it.lines()[0] } ?: ""
    return rootCause.run {
        this::class.simpleName + messagePart + stackTrace?.firstOrNull()
            ?.let { element -> " at.(${element.fileName}:${element.lineNumber})" }
    }
}

public fun Result<*>?.toCompactString(): String {
    if (this == null) return ""
    return if (isSuccess) getOrNull().toCompactString()
    else exceptionOrNull().toCompactString()
}

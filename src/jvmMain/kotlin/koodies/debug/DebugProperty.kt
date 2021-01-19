package koodies.debug

import koodies.debug.Debug.defaultAnsiPrefix
import koodies.debug.Debug.defaultAnsiSuffix
import koodies.debug.Debug.meta
import koodies.debug.Debug.secondaryMeta
import koodies.terminal.AnsiColors.brightCyan
import koodies.terminal.AnsiColors.cyan
import koodies.terminal.AnsiColors.gray
import koodies.text.CodePoint.Companion.asCodePoint
import koodies.text.withoutPrefix
import koodies.text.withoutSuffix
import koodies.text.wrap

object Debug {
    fun CharSequence.meta() = brightCyan()
    fun CharSequence.secondaryMeta() = cyan()
    val defaultPrefix = "❬"
    val defaultSuffix = "❭"
    val defaultAnsiPrefix = defaultPrefix.meta()
    val defaultAnsiSuffix = defaultSuffix.meta()
    fun wrap(text: CharSequence?, prefix: String = defaultPrefix, suffix: String = defaultSuffix) =
        text?.wrap(prefix.meta(), suffix.meta()) ?: null.wrap("❬".meta(), "❭".meta())
}

inline val CharSequence?.debug: String
    get() = if (this == null) null.wrap("❬".meta(), "❭".meta())
    else toString().replaceNonPrintableCharacters().wrap("❬".meta(), "⫻".meta() + "${this.length}".gray() + "❭".meta())
inline val <T> Iterable<T>?.debug: String get() = this?.joinToString("") { it.toString().debug }.debug
inline val List<Byte>?.debug: String get() = this?.toByteArray()?.let { bytes: ByteArray -> String(bytes) }.debug
inline val Char?.debug: String get() = this.toString().replaceNonPrintableCharacters().wrap("❬", "❭")

/**
 * Contains this byte array in its debug form, e.g. `❬0x80, 0xFFÿ, 0x00␀, 0x01␁, 0x7F␡❭`
 */
inline val Byte?.debug: String
    get() = this?.let { byte: Byte ->
        StringBuilder().apply {
            append("0x".meta())
            append(String.format("%02x", byte).toUpperCase())
            append(byte.asCodePoint().string.replaceNonPrintableCharacters().secondaryMeta())
        }.toString()
    }.let { Debug.wrap(it) }
val Array<*>?.debug: String
    get() = this?.joinToString(",") {
        it.debug.withoutPrefix(defaultAnsiPrefix).withoutSuffix(defaultAnsiSuffix)
    }.let { Debug.wrap(it, prefix = "【", suffix = "】") }
inline val Boolean?.debug: String get() = asEmoji
inline val Any?.debug: String
    get() = when (this) {
        null -> "❬null❭"
        is Iterable<*> -> this.debug
        is CharSequence -> this.debug
        is ByteArray -> this.toList().toTypedArray().debug
        is Array<*> -> this.debug
        is Byte -> this.debug
        else -> toString().debug
    }
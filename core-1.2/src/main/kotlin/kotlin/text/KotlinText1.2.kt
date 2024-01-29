// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE", "unused", "TooManyFunctions")

package kotlin.text

import java.util.Locale

// StringBuilder
fun StringBuilder.append(vararg value: String?): StringBuilder {
    for (item in value)
        append(item)
    return this
}
inline fun StringBuilder.appendLine(): StringBuilder = append('\n')
inline fun StringBuilder.appendLine(value: String?): StringBuilder = append(value).appendLine()

// String extensions
inline fun String.lowercase(): String = (this as java.lang.String).toLowerCase(Locale.ROOT)
inline fun String.lowercase(locale: Locale): String = (this as java.lang.String).toLowerCase(locale)
inline fun String.uppercase(): String = (this as java.lang.String).toUpperCase(Locale.ROOT)
inline fun String.uppercase(locale: Locale): String = (this as java.lang.String).toUpperCase(locale)
inline fun String.replaceFirstChar(transform: (Char) -> CharSequence): String {
    return if (isNotEmpty()) transform(this[0]).toString() + substring(1) else this
}

// Char extensions
inline val Char.code: Int get() = this.toInt()
inline fun Char.isLowerCase(): Boolean = Character.isLowerCase(this)
public fun Char.lowercase(locale: Locale): String = toString().lowercase(locale)
inline fun Char.lowercaseChar(): Char = Character.toLowerCase(this)
inline fun Char.uppercase(): String = toString().uppercase()
fun Char.uppercase(locale: Locale): String = toString().uppercase(locale)
inline fun Char.titlecaseChar(): Char = Character.toTitleCase(this)
fun Char.titlecase(locale: Locale): String {
    val localizedUppercase = uppercase(locale)
    if (localizedUppercase.length > 1) {
        return if (this == '\u0149') localizedUppercase else localizedUppercase[0] + localizedUppercase.substring(1).lowercase()
    }
    if (localizedUppercase != uppercase()) {
        return localizedUppercase
    }
    return titlecaseChar().toString()
}

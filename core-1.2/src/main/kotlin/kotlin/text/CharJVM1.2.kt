// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("NOTHING_TO_INLINE", "unused")

package kotlin.text

import java.util.Locale

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


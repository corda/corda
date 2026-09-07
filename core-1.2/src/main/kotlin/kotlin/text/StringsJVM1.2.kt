// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE", "unused")

package kotlin.text

import java.util.Locale

// String extensions
inline fun String.lowercase(): String = (this as java.lang.String).toLowerCase(Locale.ROOT)
inline fun String.lowercase(locale: Locale): String = (this as java.lang.String).toLowerCase(locale)
inline fun String.uppercase(): String = (this as java.lang.String).toUpperCase(Locale.ROOT)
inline fun String.uppercase(locale: Locale): String = (this as java.lang.String).toUpperCase(locale)

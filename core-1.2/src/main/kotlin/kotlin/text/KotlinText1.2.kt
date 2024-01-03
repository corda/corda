// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE", "unused")

package kotlin.text

import java.util.Locale

inline fun String.lowercase(): String = (this as java.lang.String).toLowerCase(Locale.ROOT)

inline fun String.lowercase(locale: Locale): String = (this as java.lang.String).toLowerCase(locale)

// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("unused")

package kotlin.text

inline fun String.replaceFirstChar(transform: (Char) -> CharSequence): String {
    return if (isNotEmpty()) transform(this[0]).toString() + substring(1) else this
}

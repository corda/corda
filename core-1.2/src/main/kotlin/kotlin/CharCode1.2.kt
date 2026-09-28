// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("unused")
package kotlin

inline val Char.code: Int get() = this.toInt()

// Implement the new post-1.2 APIs which are used by core and serialization
@file:Suppress("NOTHING_TO_INLINE", "unused")
package kotlin.text

// StringBuilder
fun StringBuilder.append(vararg value: String?): StringBuilder {
    for (item in value)
        append(item)
    return this
}
inline fun StringBuilder.appendLine(): StringBuilder = append('\n')
inline fun StringBuilder.appendLine(value: String?): StringBuilder = append(value).appendLine()

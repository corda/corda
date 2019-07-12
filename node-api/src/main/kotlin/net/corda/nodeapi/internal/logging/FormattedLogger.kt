package net.corda.nodeapi.internal.logging

import java.util.*
import kotlin.streams.asSequence

/**
 * Formats a list of elements into a format compatible with he format required by a log tracing tool.
 */
class FormattedLogger(private val formatted: Boolean = false, private val prefix: String) {
    private val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    private val randomStringLength = 8
    private val randomString : String = Random().ints(randomStringLength.toLong(), 0, charPool.size).asSequence().map(charPool::get).joinToString("")

    private fun escapeCharacters(string: String) = string.replace("\"", "\\\"").replace("\n", " ").replace(";",":")

    private fun quote(string: String) = "\"$string\""

    /**
     * Formats a string if <code>formatted</code> is set to <code>true</code> then
     * <code>elements</code> are treated as sequence of interlined key and value pairs and formatted <code>key=value; </code>. Each element stripped of new line characters and ";" is replaced with ":".
     * The output string is also wrapped by <code>prefix(id=</code> where id is randomly generated alphanumeric string of size 8.
     * If <code>formatted</code> is set to <code>false</code> then only evey second element is concatenates (values).
     */
    fun format(vararg elements: String): String {
        val list = mutableListOf(*elements)
        if (list.size.rem(2) != 0) {
            list.add("")
        }
        val pairs = list.chunked(2) { Pair(it[0], it[1]) }
        return if (formatted) {
            pairs.joinToString(prefix = "$prefix(id=$randomString; ", postfix = ")", separator = "; ", transform = { escapeCharacters(it.first) + "=" + quote(escapeCharacters(it.second)) })
        } else {
            pairs.joinToString(transform = { it.second })
        }
    }
}
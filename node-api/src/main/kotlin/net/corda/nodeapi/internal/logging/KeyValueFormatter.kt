package net.corda.nodeapi.internal.logging

import java.util.*
import kotlin.streams.asSequence

/**
 * Formats a list of elements as key values pairs with additional elements and separator compatible the detailed logger format.
 */
class KeyValueFormatter(private val formatted: Boolean = false, private val prefix: String) {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    private val randomStringLength = 8
    private val randomString: String = Random().ints(randomStringLength.toLong(), 0, charPool.size).asSequence().map(charPool::get)
            .joinToString("")
    private val newLineRegex = "\r|\n".toRegex()

    private fun escapeCharacters(string: String) = string.replace(newLineRegex, " ").replace(";", "\\;").replace("\"","\\\"")

    private fun quote(string: String) = "\"$string\""

    /**
     * Formats a string if <code>formatted</code> is set to <code>true</code> then
     * <code>elements</code> are formatted as sequence of <code>key=value;</code>.
     * Each new lines are replace with the white space character and the <code>;\<code> characters are escaped by <code>\\</code>.
     * The output string is also wrapped by <code>prefix(id=VALUE)</code> where VALUE is randomly generated alphanumeric string of size 8.
     * If <code>formatted</code> is set to <code>false</code> then only every second element is added to an output without additional formatting or character escaping.
     */
    fun format(vararg elements: String): String {
        val list = mutableListOf(*elements)
        if (list.size.rem(2) != 0) {
            list.add("")
        }
        val pairs = list.chunked(2) { Pair(it[0], it[1]) }
        return if (formatted) {
            pairs.joinToString(prefix = "$prefix(id=$randomString;", postfix = ")", separator = ";",
                    transform = { escapeCharacters(it.first) + "=" + quote(escapeCharacters(it.second)) })
        } else {
            pairs.joinToString(transform = { it.second })
        }
    }
}
package net.corda.nodeapi.internal.logging

import java.util.Random
import kotlin.streams.asSequence

/**
 * Formats a list of elements as key values pairs with additional elements and separator compatible the detailed logger format.
 * The <code>prefix</code> is added at the beginning of a line.
 * When <code>addId</code> is set to true, then <code>id=value</code> is added to a line,
 * where value is an alphanumeric string of size 8 which is randomly generated once and it is the same for each formatted line.
 * e.g. <code>id=8rt74ysr;key1=value1;key2=value2</code>
 */
class KeyValueFormatter(private val prefix: String, private val addId: Boolean = true) {
    companion object {
        private const val randomStringLength = 8L
        private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        private val newLineRegex = "\r|\n".toRegex()
    }

    private val randomString: String by lazy {
        Random().ints(randomStringLength, 0, charPool.size).asSequence()
                .map(charPool::get)
                .joinToString("")
    }

    private fun escapeCharacters(string: String) = string.replace(newLineRegex, " ").replace(";", "\\;")
            .replace("\"", "\\\"")

    fun format(key: String, value: String): String {
        return format(Pair(key, value))
    }

    /**
     * Formats <code>elements</code> as a sequence of <code>key=value;</code> pair.
     * New lines characters are replaced with the white space character and the <code>;\<code> characters are escaped by <code>\\</code>.
     */
    fun format(vararg elements: Pair<String, String>): String {
        val pairs = elements.toMutableList().apply {
            if (addId) add(0, Pair("id", randomString))
        }

        return pairs.joinToString(separator = ";", prefix = "$prefix(", postfix = ")",
                transform = { "${escapeCharacters(it.first)}=\"${escapeCharacters(it.second)}\"" })
    }
}
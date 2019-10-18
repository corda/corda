package net.corda.nodeapi.internal.logging

import java.util.Random
import kotlin.streams.asSequence

/**
 * Formats a list of elements as key values pairs with additional elements and separator compatible the detailed logger format.
 * The <code>prefix</code> is added at the beginning of a line.
 * When <code>addId<add> is set to true, then <code>id=value<code> is added to a line,
 * where value is an alphanumeric string of size 8 which is randomly generated once and it is the same for each formatted line.
 */
class KeyValueFormatter(private val prefix: String, private val addId: Boolean = true) {
    companion object {
        private const val randomStringLength = 8
        private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        private val newLineRegex = "\r|\n".toRegex()
    }

    private val randomString: String by lazy {
        Random().ints(randomStringLength.toLong(), 0, charPool.size).asSequence()
                .map(charPool::get)
                .joinToString("")
    }

    private fun escapeCharacters(string: String) = string.replace(newLineRegex, " ").replace(";", "\\;")
            .replace("\"", "\\\"")

    /**
     * Formats <code>elements</code> as a sequence of <code>key=value;</code> pair.
     * New lines characters are replaced with the white space character and the <code>;\<code> characters are escaped by <code>\\</code>.
     */
    fun format(vararg elements: String): String {
        val list = elements.toMutableList().apply {
            if (addId) addAll(0, listOf("id", randomString))
            if (elements.size % 2 != 0) add("")
        }

        val pairs = list.chunked(2) { Pair(it[0], it[1]) }

        return pairs.joinToString(separator = ";", prefix = "$prefix(", postfix = ")",
                transform = { "${escapeCharacters(it.first)}=\"${escapeCharacters(it.second)}\"" })
    }
}
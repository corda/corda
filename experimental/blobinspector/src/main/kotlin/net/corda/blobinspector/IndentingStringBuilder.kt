package net.corda.blobinspector

/**
 * Wrapper around a [StringBuilder] that automates the indenting of lines as they're appended to facilitate
 * pretty printing of deserialized blobs.
 *
 * @property sb The wrapped [StringBuilder]
 * @property indenting Boolean flag that indicates weather we need to pad the start of whatever text
 * currently being added to the string.
 * @property indent How deeply the next line should be offset from the first column
 */
class IndentingStringBuilder(s: String = "", private val offset: Int = 4) {
    private val sb = StringBuilder(s)
    private var indenting = true
    private var indent = 0

    private fun wrap(ln: String, appender: (String) -> Unit) {
        if ((ln.endsWith("}") || ln.endsWith("]")) && indent > 0 && ln.length == 1) {
            indent -= offset
        }

        appender(ln)

        if (ln.endsWith("{") || ln.endsWith("[")) {
            indent += offset
        }
    }

    fun appendln(ln: String) {
        wrap(ln) { s -> sb.appendln("${"".padStart(if (indenting) indent else 0, ' ')}$s") }

        indenting = true
    }

    fun append(ln: String) {
        indenting = false

        wrap(ln) { s -> sb.append("${"".padStart(indent, ' ')}$s") }
    }

    override fun toString(): String {
        return sb.toString()
    }
}
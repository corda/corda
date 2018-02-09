package net.corda.behave.monitoring

class PatternWatch(
        pattern: String,
        ignoreCase: Boolean = false
) : Watch() {

    private val regularExpression = if (ignoreCase) {
        Regex("^.*$pattern.*$", RegexOption.IGNORE_CASE)
    } else {
        Regex("^.*$pattern.*$")
    }

    override fun match(data: String) = regularExpression.matches(data.trim())

    companion object {

        val EMPTY = PatternWatch("")

    }

}
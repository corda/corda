package net.corda.explorer.formatters

object NumberFormatter {
    // TODO replace this once we settled on how we do formatting
    val boring = object : Formatter<Any> {
        override fun format(value: Any) = value.toString()
    }

    val boringLong: Formatter<Long> = boring
}

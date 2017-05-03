package net.corda.explorer.formatters

object FlowNameFormatter {
    val boring = object : Formatter<String> {
        override fun format(value: String) = value.split('.').last().replace("$", ": ") // TODO Better handling of names.
    }
}
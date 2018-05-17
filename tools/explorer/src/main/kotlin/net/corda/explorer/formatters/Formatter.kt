package net.corda.explorer.formatters

interface Formatter<in T> {
    fun format(value: T): String
}

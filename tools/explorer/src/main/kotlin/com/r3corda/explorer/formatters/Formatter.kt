package com.r3corda.explorer.formatters


interface Formatter<in T> {
    fun format(value: T): String
}

package com.r3corda.explorer.formatters


interface Formatter<T> {
    fun format(value: T): String
}

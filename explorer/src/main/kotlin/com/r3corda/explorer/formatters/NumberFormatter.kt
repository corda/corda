package com.r3corda.explorer.formatters

import java.text.DecimalFormat


class NumberFormatter {
    companion object {
        private val doubleCommaFormatter = DecimalFormat("#,###.00")
        private val integralCommaFormatter = DecimalFormat("#,###")

        val doubleComma = object : Formatter<Double> {
            override fun format(value: Double) =
                    doubleCommaFormatter.format(value)
        }

        val longComma = object : Formatter<Long> {
            override fun format(value: Long) =
                    integralCommaFormatter.format(value)
        }
        val intComma = object : Formatter<Int> {
            override fun format(value: Int) =
                    integralCommaFormatter.format(value)
        }
    }
}

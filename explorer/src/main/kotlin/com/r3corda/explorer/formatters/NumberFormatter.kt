package com.r3corda.explorer.formatters

import java.text.DecimalFormat


class NumberFormatter {
    companion object {
        private val doubleCommaFormatter = DecimalFormat("#,###.00")
        private val integralCommaFormatter = DecimalFormat("#,###")

        private val _integralComma: Formatter<Any> = object : Formatter<Any> {
            override fun format(value: Any) = integralCommaFormatter.format(value)
        }

        val doubleComma = object : Formatter<Double> {
            override fun format(value: Double) = doubleCommaFormatter.format(value)
        }

        val numberComma: Formatter<Number> = _integralComma
        val longComma: Formatter<Long> = _integralComma
    }
}

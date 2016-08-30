package com.r3corda.explorer.formatters

import com.r3corda.core.contracts.Amount
import java.text.DecimalFormat
import java.util.*

class CurrencyFormatter {

    companion object {
        private val commaFormatter = DecimalFormat("#,###.00")

        fun currency(formatter: Formatter<Amount<Currency>>) = object : Formatter<Amount<Currency>> {
            override fun format(value: Amount<Currency>) =
                    "${value.token.currencyCode} ${formatter.format(value)}"
        }

        val comma = object : Formatter<Amount<Currency>> {
            override fun format(value: Amount<Currency>) =
                    commaFormatter.format(value.quantity / 100.0)
        }

        data class KmbRange(val fromLog: Double, val toLog: Double, val letter: String, val divider: (Double) -> Double)
        val kmbRanges = listOf(
                KmbRange(Double.NEGATIVE_INFINITY, Math.log(1000.0), "", { value -> value }),
                KmbRange(Math.log(1000.0), Math.log(1000000.0), "k", { value -> value / 1000.0 }),
                KmbRange(Math.log(1000000.0), Math.log(1000000000.0), "m", { value -> value / 1000000.0 }),
                KmbRange(Math.log(1000000000.0), Double.POSITIVE_INFINITY, "b", { value -> value / 1000000000.0 })
        )

        val kmbComma = object : Formatter<Amount<Currency>> {
            override fun format(value: Amount<Currency>): String {
                val displayAmount = value.quantity / 100.0
                val logarithm = Math.log(displayAmount)
                val rangeIndex = kmbRanges.binarySearch(
                        comparison = { range ->
                            if (logarithm < range.fromLog) {
                                1
                            } else if (logarithm < range.toLog) {
                                0
                            } else {
                                -1
                            }
                        }
                )
                val kmbRange = kmbRanges[rangeIndex]
                return "${commaFormatter.format(kmbRange.divider(displayAmount))}${kmbRange.letter}"
            }
        }
    }
}

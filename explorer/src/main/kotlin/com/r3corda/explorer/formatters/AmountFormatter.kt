package com.r3corda.explorer.formatters

import com.ibm.icu.text.DecimalFormat
import com.r3corda.core.contracts.Amount
import humanize.icu.spi.context.DefaultICUContext
import java.util.Currency

class AmountFormatter {

    companion object {

        val icuContext = DefaultICUContext()

        fun currency(formatter: Formatter<Amount<Currency>>) = object : Formatter<Amount<Currency>> {
            override fun format(value: Amount<Currency>) =
                    "${value.token.currencyCode} ${formatter.format(value)}"
        }

        val comma = object : Formatter<Amount<Currency>> {
            override fun format(value: Amount<Currency>) =
                    NumberFormatter.doubleComma.format(value.quantity / 100.0)
        }

        val compact = object : Formatter<Amount<Currency>> {
            val decimalFormat = (icuContext.compactDecimalFormat as DecimalFormat).apply {
                minimumFractionDigits = 0
                maximumFractionDigits = 4
                setSignificantDigitsUsed(false)
            }
            override fun format(value: Amount<Currency>): String {
                return decimalFormat.format(value.quantity / 100.0)
            }
        }
    }
}

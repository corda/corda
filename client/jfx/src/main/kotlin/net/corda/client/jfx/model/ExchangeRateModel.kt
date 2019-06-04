package net.corda.client.jfx.model

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import net.corda.core.contracts.Amount
import net.corda.finance.CHF
import net.corda.finance.EUR
import net.corda.finance.GBP
import net.corda.finance.USD
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

/**
 * This model provides an exchange rate from arbitrary currency to arbitrary currency.
 */
abstract class ExchangeRate {
    /**
     * Convert the given amount of a currency into the target currency.
     *
     * @return the original amount converted to an amount in the target currency.
     */
    fun exchangeAmount(amount: Amount<Currency>, to: Currency) = Amount.fromDecimal(amount.toDecimal().multiply(rate(amount.token, to)), to)

    abstract fun rate(from: Currency, to: Currency): BigDecimal
}

/**
 * Default implementation of an exchange rate model, which uses a fixed exchange rate.
 */
private val usdExchangeRates: Map<Currency, BigDecimal> = mapOf(
        GBP to BigDecimal(1.31),
        EUR to BigDecimal(1.18),
        CHF to BigDecimal(1.01)
)

private fun safeFetchRate(currency: Currency) =
        usdExchangeRates[currency] ?: throw IllegalArgumentException("No exchange rate for $currency")

// TODO hook up an actual oracle
class ExchangeRateModel {
    val exchangeRate: ObservableValue<ExchangeRate> = SimpleObjectProperty<ExchangeRate>(object : ExchangeRate() {
        override fun rate(from: Currency, to: Currency): BigDecimal =
                when {
                    from == to -> BigDecimal.ONE
                    USD == to -> safeFetchRate(from)
                    USD == from -> BigDecimal.ONE.divide(safeFetchRate(to), MathContext.DECIMAL64)
                    else -> safeFetchRate(from).divide(safeFetchRate(to), MathContext.DECIMAL64)
                }
    })
}

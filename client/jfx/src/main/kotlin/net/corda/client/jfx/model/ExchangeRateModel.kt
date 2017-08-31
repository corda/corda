package net.corda.client.jfx.model

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import net.corda.core.contracts.Amount
import java.math.BigDecimal
import java.math.RoundingMode
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
// TODO hook up an actual oracle
class ExchangeRateModel {
    val exchangeRate: ObservableValue<ExchangeRate> = SimpleObjectProperty<ExchangeRate>(object : ExchangeRate() {
        override fun rate(from: Currency, to: Currency) = BigDecimal.ONE
    })
}

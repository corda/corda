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
    fun exchangeAmount(amount: Amount<Currency>, to: Currency, roundingMode: RoundingMode = RoundingMode.HALF_UP) =
            amount.multiply(rate(amount.token, to), roundingMode)
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

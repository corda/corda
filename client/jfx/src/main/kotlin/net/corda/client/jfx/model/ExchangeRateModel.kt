package net.corda.client.jfx.model

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import net.corda.core.contracts.Amount
import java.util.*

/**
 * This model provides an exchange rate from arbitrary currency to arbitrary currency.
 */
abstract class ExchangeRate {
    fun exchangeAmount(amount: Amount<Currency>, to: Currency) =
            Amount(exchangeDouble(amount, to).toLong(), to)

    fun exchangeDouble(amount: Amount<Currency>, to: Currency) =
            rate(amount.token, to) * amount.quantity
    abstract fun rate(from: Currency, to: Currency): Double
}

/**
 * Default implementation of an exchange rate model, which uses a fixed exchange rate.
 */
// TODO hook up an actual oracle
class ExchangeRateModel {
    val exchangeRate: ObservableValue<ExchangeRate> = SimpleObjectProperty<ExchangeRate>(object : ExchangeRate() {
        override fun rate(from: Currency, to: Currency) = 1.0
    })
}

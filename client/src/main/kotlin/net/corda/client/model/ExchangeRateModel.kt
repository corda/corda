package net.corda.client.model

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import net.corda.core.contracts.Amount
import java.util.*


interface ExchangeRate {
    fun rate(from: Currency, to: Currency): Double
}

fun ExchangeRate.exchangeAmount(amount: Amount<Currency>, to: Currency) =
        Amount(exchangeDouble(amount, to).toLong(), to)

fun ExchangeRate.exchangeDouble(amount: Amount<Currency>, to: Currency) =
        rate(amount.token, to) * amount.quantity

/**
 * This model provides an exchange rate from arbitrary currency to arbitrary currency.
 * TODO hook up an actual oracle
 */
class ExchangeRateModel {
    val exchangeRate: ObservableValue<ExchangeRate> = SimpleObjectProperty<ExchangeRate>(object : ExchangeRate {
        override fun rate(from: Currency, to: Currency) = 1.0
    })
}

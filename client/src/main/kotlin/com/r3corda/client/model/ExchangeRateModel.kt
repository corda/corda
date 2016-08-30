package com.r3corda.client.model

import com.r3corda.core.contracts.Amount
import javafx.beans.property.SimpleObjectProperty
import java.util.*


interface ExchangeRate {
    fun rate(from: Currency, to: Currency): Double
}
fun ExchangeRate.exchangeAmount(amount: Amount<Currency>, to: Currency) =
        Amount(exchangeDouble(amount, to).toLong(), to)
fun ExchangeRate.exchangeDouble(amount: Amount<Currency>, to: Currency) =
        rate(amount.token, to) * amount.quantity

class ExchangeRateModel {
    // TODO hook up an actual oracle
    val exchangeRate = SimpleObjectProperty<ExchangeRate>(object : ExchangeRate {
        override fun rate(from: Currency, to: Currency) = 1.0
    })
}

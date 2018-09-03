package net.corda.client.jfx.utils

import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import net.corda.client.jfx.model.ExchangeRate
import net.corda.core.contracts.Amount
import org.fxmisc.easybind.EasyBind
import org.fxmisc.easybind.monadic.MonadicBinding
import java.util.*
import java.util.stream.Collectors

/**
 * Utility bindings for the [Amount] type, similar in spirit to [Bindings]
 */
object AmountBindings {
    fun <T : Any> sum(amounts: ObservableList<Amount<T>>, token: T): MonadicBinding<Amount<T>> = EasyBind.map(
            Bindings.createLongBinding({
                amounts.stream().collect(Collectors.summingLong {
                    require(it.token == token)
                    it.quantity
                })
            }, arrayOf(amounts))
    ) { sum -> Amount(sum.toLong(), token) }

    fun exchange(
            observableCurrency: ObservableValue<Currency>,
            observableExchangeRate: ObservableValue<ExchangeRate>
    ): ObservableValue<Pair<Currency, (Amount<Currency>) -> Long>> {
        return EasyBind.combine(observableCurrency, observableExchangeRate) { currency, exchangeRate ->
            Pair<Currency, (Amount<Currency>) -> Long>(
                    currency,
                    { amount -> exchangeRate.exchangeAmount(amount, currency).quantity }
            )
        }
    }

    fun sumAmountExchange(
            amounts: ObservableList<Amount<Currency>>,
            currency: ObservableValue<Currency>,
            exchangeRate: ObservableValue<ExchangeRate>
    ): ObservableValue<Amount<Currency>> {
        return EasyBind.monadic(exchange(currency, exchangeRate)).flatMap {
            val (currencyValue, exchange: (Amount<Currency>) -> Long) = it
            EasyBind.map(
                    Bindings.createLongBinding({
                        amounts.stream().collect(Collectors.summingLong { exchange(it) })
                    }, arrayOf(amounts))
            ) { Amount(it.toLong(), currencyValue) }
        }
    }
}

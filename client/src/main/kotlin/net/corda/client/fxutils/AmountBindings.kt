package net.corda.client.fxutils

import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import kotlinx.support.jdk8.collections.stream
import net.corda.client.model.ExchangeRate
import net.corda.core.contracts.Amount
import org.fxmisc.easybind.EasyBind
import java.util.*
import java.util.stream.Collectors

/**
 * Utility bindings for the [Amount] type, similar in spirit to [Bindings]
 */
object AmountBindings {
    fun <T> sum(amounts: ObservableList<Amount<T>>, token: T) = EasyBind.map(
            Bindings.createLongBinding({
                amounts.stream().collect(Collectors.summingLong {
                    require(it.token == token)
                    it.quantity
                })
            }, arrayOf(amounts))
    ) { sum -> Amount(sum.toLong(), token) }

    fun exchange(
            currency: ObservableValue<Currency>,
            exchangeRate: ObservableValue<ExchangeRate>
    ): ObservableValue<Pair<Currency, (Amount<Currency>) -> Long>> {
        return EasyBind.combine(currency, exchangeRate) { currency, exchangeRate ->
            Pair(currency) { amount: Amount<Currency> ->
                (exchangeRate.rate(amount.token, currency) * amount.quantity).toLong()
            }
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

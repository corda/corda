package net.corda.explorer.model

import javafx.beans.value.ObservableValue
import net.corda.client.jfx.model.ExchangeRate
import net.corda.client.jfx.model.ExchangeRateModel
import net.corda.client.jfx.model.observableValue
import net.corda.client.jfx.utils.AmountBindings
import net.corda.core.contracts.Amount
import org.fxmisc.easybind.EasyBind
import java.util.*

class ReportingCurrencyModel {
    private val exchangeRate: ObservableValue<ExchangeRate> by observableValue(ExchangeRateModel::exchangeRate)
    private val reportingCurrency by observableValue(SettingsModel::reportingCurrencyProperty)

    /**
     * This stream provides a stream of exchange() functions that updates when either the reporting currency or the
     * exchange rates change
     */
    val reportingExchange: ObservableValue<Pair<Currency, (Amount<Currency>) -> Amount<Currency>>> =
            EasyBind.map(AmountBindings.exchange(reportingCurrency, exchangeRate)) {
                Pair(it.first) { amount: Amount<Currency> ->
                    Amount(it.second(amount), it.first)
                }
            }
}

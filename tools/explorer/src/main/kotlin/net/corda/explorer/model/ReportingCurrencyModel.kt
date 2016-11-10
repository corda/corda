package net.corda.explorer.model

import net.corda.client.fxutils.AmountBindings
import net.corda.client.model.ExchangeRate
import net.corda.client.model.ExchangeRateModel
import net.corda.client.model.observableValue
import net.corda.core.contracts.Amount
import javafx.beans.value.ObservableValue
import org.fxmisc.easybind.EasyBind
import java.util.*

class ReportingCurrencyModel {
    private val exchangeRate: ObservableValue<ExchangeRate> by observableValue(ExchangeRateModel::exchangeRate)
    val reportingCurrency: ObservableValue<Currency> by observableValue(SettingsModel::reportingCurrency)
    /**
     * This stream provides a stream of exchange() functions that updates when either the reporting currency or the
     * exchange rates change
     */
    val reportingExchange: ObservableValue<Pair<Currency, (Amount<Currency>) -> Amount<Currency>>> =
            EasyBind.map(AmountBindings.exchange(reportingCurrency, exchangeRate)) { Pair(it.first) { amount: Amount<Currency> ->
                Amount(it.second(amount), it.first)
            }}
}

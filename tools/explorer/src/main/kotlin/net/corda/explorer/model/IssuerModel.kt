package net.corda.explorer.model

import javafx.collections.FXCollections
import net.corda.client.jfx.model.NodeMonitorModel
import net.corda.client.jfx.model.observableValue
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.map
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashConfigDataFlow
import tornadofx.*
import java.util.*

class IssuerModel {

    private val defaultCurrency = Currency.getInstance("USD")

    private val proxy by observableValue(NodeMonitorModel::proxyObservable)
    private val cashAppConfiguration = proxy.map { it?.cordaRPCOps?.startFlow(::CashConfigDataFlow)?.returnValue?.getOrThrow() }
    val supportedCurrencies = ChosenList(cashAppConfiguration.map { it?.supportedCurrencies?.observable() ?: FXCollections.singletonObservableList(defaultCurrency) }, "supportedCurrencies")
    val currencyTypes = ChosenList(cashAppConfiguration.map { it?.issuableCurrencies?.observable() ?: FXCollections.emptyObservableList() }, "currencyTypes")

    val transactionTypes = ChosenList(cashAppConfiguration.map {
        if (it?.issuableCurrencies?.isNotEmpty() == true)
            CashTransaction.values().asList().observable()
        else
            listOf(CashTransaction.Pay).observable()
    }, "transactionTypes")
}

package net.corda.explorer.model

import javafx.collections.ObservableList
import net.corda.client.jfx.model.NetworkIdentityModel
import net.corda.client.jfx.model.observableList
import net.corda.client.jfx.model.observableValue
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.map
import net.corda.core.contracts.currency
import net.corda.core.node.NodeInfo
import tornadofx.*

val ISSUER_SERVICE_TYPE = Regex("corda.issuer.(USD|GBP|CHF|EUR)")

class IssuerModel {
    private val networkIdentities by observableList(NetworkIdentityModel::networkIdentities)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val supportedCurrencies by observableList(ReportingCurrencyModel::supportedCurrencies)

    val issuers: ObservableList<NodeInfo> = networkIdentities.filtered { it.advertisedServices.any { it.info.type.id.matches(ISSUER_SERVICE_TYPE) } }

    val currencyTypes = ChosenList(myIdentity.map {
        it?.issuerCurrency()?.let { (listOf(it)).observable() } ?: supportedCurrencies
    })

    val transactionTypes = ChosenList(myIdentity.map {
        if (it?.isIssuerNode() ?: false)
            CashTransaction.values().asList().observable()
        else
            listOf(CashTransaction.Pay).observable()
    })

    private fun NodeInfo.isIssuerNode() = advertisedServices.any { it.info.type.id.matches(ISSUER_SERVICE_TYPE) }

    private fun NodeInfo.issuerCurrency() = if (isIssuerNode()) {
        val issuer = advertisedServices.first { it.info.type.id.matches(ISSUER_SERVICE_TYPE) }
        currency(issuer.info.type.id.substringAfterLast("."))
    } else
        null
}

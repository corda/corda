package net.corda.explorer.model

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.model.NetworkIdentityModel
import net.corda.client.jfx.model.observableList
import net.corda.client.jfx.model.observableValue
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.map
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import tornadofx.*

val ISSUER_SERVICE_TYPE = Regex("corda.issuer.(USD|GBP|CHF|EUR)")

class IssuerModel {
    // TODO Explorer will be fixed as separate PR.
    private val networkIdentities by observableList(NetworkIdentityModel::networkIdentities)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val supportedCurrencies by observableList(ReportingCurrencyModel::supportedCurrencies)

    val issuers: ObservableList<NodeInfo> = FXCollections.observableList(networkIdentities)

    val currencyTypes = ChosenList(myIdentity.map { supportedCurrencies })

    val transactionTypes = ChosenList(myIdentity.map {
        if (it?.isIssuerNode() ?: false)
            CashTransaction.values().asList().observable()
        else
            listOf(CashTransaction.Pay).observable()
    })

    private fun Party.isIssuerNode() = true
}

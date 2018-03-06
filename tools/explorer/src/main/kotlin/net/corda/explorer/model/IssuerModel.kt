/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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

class IssuerModel {
    private val proxy by observableValue(NodeMonitorModel::proxyObservable)
    private val cashAppConfiguration = proxy.map { it?.startFlow(::CashConfigDataFlow)?.returnValue?.getOrThrow() }
    val supportedCurrencies = ChosenList(cashAppConfiguration.map { it?.supportedCurrencies?.observable() ?: FXCollections.emptyObservableList() })
    val currencyTypes = ChosenList(cashAppConfiguration.map { it?.issuableCurrencies?.observable() ?: FXCollections.emptyObservableList() })

    val transactionTypes = ChosenList(cashAppConfiguration.map {
        if (it?.issuableCurrencies?.isNotEmpty() == true)
            CashTransaction.values().asList().observable()
        else
            listOf(CashTransaction.Pay).observable()
    })
}

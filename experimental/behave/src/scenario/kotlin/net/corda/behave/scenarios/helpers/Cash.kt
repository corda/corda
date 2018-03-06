/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.scenarios.helpers

import net.corda.behave.scenarios.ScenarioState
import net.corda.core.messaging.startFlow
import net.corda.finance.flows.CashConfigDataFlow
import java.util.concurrent.TimeUnit

class Cash(state: ScenarioState) : Substeps(state) {

    fun numberOfIssuableCurrencies(nodeName: String): Int {
        return withClient(nodeName) {
            for (flow in it.registeredFlows()) {
                log.info(flow)
            }
            try {
                val config = it.startFlow(::CashConfigDataFlow).returnValue.get(10, TimeUnit.SECONDS)
                for (supportedCurrency in config.supportedCurrencies) {
                    log.info("Can use $supportedCurrency")
                }
                for (issuableCurrency in config.issuableCurrencies) {
                    log.info("Can issue $issuableCurrency")
                }
                return@withClient config.issuableCurrencies.size
            } catch (ex: Exception) {
                log.warn("Failed to retrieve cash configuration data", ex)
                throw ex
            }
        }
    }

}
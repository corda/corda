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
            } catch (_: Exception) {
                return@withClient 0
            }
        }
    }

}
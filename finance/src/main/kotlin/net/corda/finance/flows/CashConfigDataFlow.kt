package net.corda.finance.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CordaSerializable
import net.corda.finance.CHF
import net.corda.finance.EUR
import net.corda.finance.GBP
import net.corda.finance.USD
import java.util.*

/**
 * Flow to obtain cash cordapp app configuration.
 */
@StartableByRPC
class CashConfigDataFlow : FlowLogic<CashConfiguration>() {
    companion object {
        private val supportedCurrencies = listOf(USD, GBP, CHF, EUR)
    }

    @Suspendable
    override fun call(): CashConfiguration {
        val issuableCurrencies = supportedCurrencies.mapNotNull {
            try {
                // Currently it uses checkFlowPermission to determine the list of issuable currency as a temporary hack.
                // TODO: get the config from proper configuration source.
                checkFlowPermission("corda.issuer.$it", emptyMap())
                it
            } catch (e: FlowException) {
                null
            }
        }
        return CashConfiguration(issuableCurrencies, supportedCurrencies)
    }
}

@CordaSerializable
data class CashConfiguration(val issuableCurrencies: List<Currency>, val supportedCurrencies: List<Currency>)

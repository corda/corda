package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CordaSerializable
import com.r3.corda.enterprise.perftestcordapp.CHF
import com.r3.corda.enterprise.perftestcordapp.EUR
import com.r3.corda.enterprise.perftestcordapp.GBP
import com.r3.corda.enterprise.perftestcordapp.USD
import java.util.*

/**
 * Flow to obtain cash cordapp app configuration.
 */
@StartableByRPC
class PtCashConfigDataFlow : FlowLogic<PtCashConfiguration>() {
    companion object {
        private val supportedCurrencies = listOf(USD, GBP, CHF, EUR)
    }

    @Suspendable
    override fun call(): PtCashConfiguration {
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
        return PtCashConfiguration(issuableCurrencies, supportedCurrencies)
    }
}

@CordaSerializable
data class PtCashConfiguration(val issuableCurrencies: List<Currency>, val supportedCurrencies: List<Currency>)

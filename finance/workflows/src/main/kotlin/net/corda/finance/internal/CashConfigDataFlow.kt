package net.corda.finance.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.finance.CHF
import net.corda.finance.EUR
import net.corda.finance.GBP
import net.corda.finance.USD
import net.corda.finance.internal.ConfigHolder.Companion.supportedCurrencies
import java.util.*

@CordaService
class ConfigHolder(services: AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val supportedCurrencies = listOf(USD, GBP, CHF, EUR)
    }

    val issuableCurrencies: List<Currency>

    init {
        val config = services.getAppContext().config
        val issuableCurrenciesStringList: List<String> = if (config.exists("issuableCurrencies")) {
            uncheckedCast(config.get("issuableCurrencies"))
        } else {
            emptyList()
        }
        issuableCurrencies = issuableCurrenciesStringList.map(Currency::getInstance)
        (issuableCurrencies - supportedCurrencies).let {
            require(it.isEmpty()) { "$it are not supported currencies" }
        }
    }
}

/**
 * Flow to obtain cash cordapp app configuration.
 */
@StartableByRPC
class CashConfigDataFlow : FlowLogic<CashConfiguration>() {
    @Suspendable
    override fun call(): CashConfiguration {
        val configHolder = serviceHub.cordaService(ConfigHolder::class.java)
        return CashConfiguration(configHolder.issuableCurrencies, supportedCurrencies)
    }
}

@CordaSerializable
data class CashConfiguration(val issuableCurrencies: List<Currency>, val supportedCurrencies: List<Currency>)

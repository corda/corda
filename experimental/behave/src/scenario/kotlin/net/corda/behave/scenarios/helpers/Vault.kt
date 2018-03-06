package net.corda.behave.scenarios.helpers

import net.corda.behave.scenarios.ScenarioState
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashConfigDataFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import java.util.*
import java.util.concurrent.TimeUnit

class Vault(state: ScenarioState) : Substeps(state) {

    fun <T: ContractState> query(nodeName: String, contractStateType: Class<out T>): Int {
        return withClient(nodeName) {
            try {
                val results = it.vaultQuery(contractStateType)
                log.info("Vault query return results: $results")
                return@withClient results.states.size
            } catch (ex: Exception) {
                log.warn("Failed to retrieve cash configuration data", ex)
                throw ex
            }
        }
    }
}
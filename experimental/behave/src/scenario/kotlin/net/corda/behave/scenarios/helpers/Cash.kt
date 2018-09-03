package net.corda.behave.scenarios.helpers

import net.corda.behave.scenarios.ScenarioState
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Amount
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashConfigDataFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import java.util.*
import java.util.concurrent.TimeUnit

class Cash(state: ScenarioState) : Substeps(state) {

    fun numberOfIssuableCurrencies(nodeName: String): Int {
        return withClient(nodeName) {
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

    fun issueCash(issueToNode: String, amount: Long, currency: String): SignedTransaction {
        return withClient(issueToNode) {
            try {
                val notaryList = it.notaryIdentities()
                if (notaryList.isEmpty())
                    throw CordaRuntimeException("No Notaries configured in this network.")
                val notaryParty = notaryList[0]
                return@withClient it.startFlow(::CashIssueFlow, Amount(amount, Currency.getInstance(currency)), OpaqueBytes.of(1), notaryParty).returnValue.getOrThrow().stx
            } catch (ex: Exception) {
                log.warn("Failed to issue $amount $currency cash to $issueToNode", ex)
                throw ex
            }
        }
    }

    fun transferCash(senderNode: String, sendToNode: String, amount: Long, currency: String): SignedTransaction {
        return withClient(senderNode) {
            try {
                val sendToX500Name = node(sendToNode).config.cordaX500Name
                val sendToParty = node(senderNode).rpc {
                    it.wellKnownPartyFromX500Name(sendToX500Name) ?: throw IllegalStateException("Unable to locate $sendToX500Name in Network Map Service")
                }
                return@withClient it.startFlow(::CashPaymentFlow, Amount(amount, Currency.getInstance(currency)), sendToParty).returnValue.getOrThrow().stx
            } catch (ex: Exception) {
                log.warn("Failed to transfer $amount cash from $senderNode to $sendToNode", ex)
                throw ex
            }
        }
    }
}
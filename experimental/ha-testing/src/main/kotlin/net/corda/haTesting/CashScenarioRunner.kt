package net.corda.haTesting

import joptsimple.OptionSet
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.finance.GBP
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import java.util.concurrent.Callable

// Responsible for executing test scenario for 2 nodes and verifying the outcome
class CashScenarioRunner(options: OptionSet) : AbstractScenarioRunner(options), Callable<Boolean> {

    companion object {
        private val logger = contextLogger()
    }

    override fun call(): Boolean {
        // It is assumed that normal Node is capable of issuing.
        // Create a unique tag for this issuance round
        val issuerBankPartyRef = SecureHash.randomSHA256().bytes
        val currency = GBP
        val issueAmount = Amount(iterCount * 100L, currency)
        logger.info("Trying: issue to normal, amount: $issueAmount")
        val issueOutcome = normalNodeRpcOps.startFlow(::CashIssueFlow, issueAmount, OpaqueBytes(issuerBankPartyRef), notary).returnValue.getOrThrow()
        logger.info("Success: issue to normal, amount: $issueAmount, TX ID: ${issueOutcome.stx.id}")

        scenarioInitialized()

        try {
            val initialAmount: Long = iterCount * 10L
            require(initialAmount > iterCount)

            val allPayments = mutableListOf<AbstractCashFlow.Result>()

            for (iterNo in 1..iterCount) {
                val transferQuantity = issueAmount.quantity
                logger.info("#$iterNo.1 - Trying: normal -> ha, amount: ${transferQuantity}p")
                val firstPayment = normalNodeRpcOps.startFlow(::CashPaymentFlow, Amount(transferQuantity, currency), haNodeParty, false).returnValue.getOrThrow()
                logger.info("#$iterNo.2 - Success: normal -> ha, amount: ${transferQuantity}p, TX ID: ${firstPayment.stx.id}")
                allPayments.add(firstPayment)

                val transferBackQuantity = transferQuantity
                logger.info("#$iterNo.3 - Trying: ha -> normal, amount: ${transferBackQuantity}p")
                val secondPayment = haNodeRpcOps.startFlowWithRetryAndGet(::CashPaymentFlow, Amount(transferBackQuantity, currency), normalNodeParty, false)
                logger.info("#$iterNo.4 - Success: ha -> normal, amount: ${transferBackQuantity}p, TX ID: ${secondPayment.stx.id}")
                allPayments.add(secondPayment)
            }

            // Verify
            assert(allPayments.size == (iterCount * 2)) { "Expected number of payments is ${iterCount * 2}, actual number of payments: ${allPayments.size}" }
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
            // TODO: Potentially implement paging validation logic for bigger data sets.
            val pageSpecification = PageSpecification(pageNumber = 1, pageSize = Int.MAX_VALUE)
            val normalStates = normalNodeRpcOps.vaultQueryBy<Cash.State>(criteria, pageSpecification)
            val haStates = haNodeRpcOps.vaultQueryByWithRetry<Cash.State>(criteria, pageSpecification)
            return verifyPaymentsAndStatesTally(allPayments, mapOf(normalNodeParty to normalStates, haNodeParty to haStates))
        } finally {
            scenarioCompleted()
        }
    }

    private fun verifyPaymentsAndStatesTally(allPayments: MutableList<AbstractCashFlow.Result>, statesByParty: Map<Party, Vault.Page<Cash.State>>): Boolean {

        val hashesByParty: Map<Party, Set<SecureHash>> = statesByParty.mapValues { entry -> entry.value.states.map { state -> state.ref.txhash }.toSet() }

        // Check that TX reference is present in both set of states.
        allPayments.forEach { payment ->
            val transactionId = payment.stx.id
            val recipient = payment.recipient

            val allStatesForParty = hashesByParty[recipient] ?: throw IllegalArgumentException("Cannot find states for party: $recipient in transaction: $transactionId")

            // Recipient definitely should have hash of a transaction in its states.
            assert(transactionId in allStatesForParty) { "States for party: $recipient should contain reference: $transactionId" }
        }

        return true
    }
}
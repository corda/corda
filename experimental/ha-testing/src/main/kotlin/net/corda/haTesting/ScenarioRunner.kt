package net.corda.haTesting

import joptsimple.OptionSet
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.*
import net.corda.finance.GBP
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import java.util.concurrent.Callable

// Responsible for executing test scenario for 2 nodes and verifying the outcome
class ScenarioRunner(private val options: OptionSet) : Callable<Boolean> {

    companion object {
        private val logger = contextLogger()

        private fun establishRpcConnection(endpoint: NetworkHostAndPort, user: String, password: String,
                                           onError: (Throwable) -> CordaRPCOps =
                                                   {
                                                       logger.error("establishRpcConnection", it)
                                                       throw it
                                                   }): CordaRPCOps {
            try {
                val retryInterval = 5.seconds

                val client = CordaRPCClient(endpoint,
                    object : CordaRPCClientConfiguration {
                        override val connectionMaxRetryInterval = retryInterval
                    }
                )
                val connection = client.start(user, password)
                return connection.proxy
            } catch (th: Throwable) {
                return onError(th)
            }
        }
    }

    override fun call(): Boolean {
        val haNodeRpcOps = establishRpcConnection(
                options.valueOf(MandatoryCommandLineArguments.haNodeRpcAddress.name) as NetworkHostAndPort,
                options.valueOf(MandatoryCommandLineArguments.haNodeRpcUserName.name) as String,
                options.valueOf(MandatoryCommandLineArguments.haNodeRpcPassword.name) as String
        )
        val haNodeParty = haNodeRpcOps.nodeInfo().legalIdentities.first()

        val normalNodeRpcOps = establishRpcConnection(
                options.valueOf(MandatoryCommandLineArguments.normalNodeRpcAddress.name) as NetworkHostAndPort,
                options.valueOf(MandatoryCommandLineArguments.normalNodeRpcUserName.name) as String,
                options.valueOf(MandatoryCommandLineArguments.normalNodeRpcPassword.name) as String
        )
        val normalNodeParty = normalNodeRpcOps.nodeInfo().legalIdentities.first()

        val notary = normalNodeRpcOps.notaryIdentities().first()

        val iterCount = options.valueOf(OptionalCommandLineArguments.iterationsCount.name) as Int? ?: 10
        logger.info("Total number of iterations to run: $iterCount")

        // It is assumed that normal Node is capable of issuing.
        // Create a unique tag for this issuance round
        val issuerBankPartyRef = SecureHash.randomSHA256().bytes
        val currency = GBP
        val issueAmount = Amount(iterCount * 100L, currency)
        logger.info("Trying: issue to normal, amount: $issueAmount")
        val issueOutcome = normalNodeRpcOps.startFlow(::CashIssueFlow, issueAmount, OpaqueBytes(issuerBankPartyRef), notary).returnValue.getOrThrow()
        logger.info("Success: issue to normal, amount: $issueAmount, TX ID: ${issueOutcome.stx.id}")

        // TODO start a daemon thread which will talk to HA Node and installs termination schedule to it
        // The daemon will monitor availability of HA Node and as soon as it is down and then back-up it will install
        // the next termination schedule.

        val initialAmount: Long = iterCount * 10L
        require(initialAmount > iterCount)

        val allPayments = mutableListOf<AbstractCashFlow.Result>()

        for(iterNo in 1 .. iterCount) {
            val transferQuantity = issueAmount.quantity
            logger.info("#$iterNo.1 - Trying: normal -> ha, amount: ${transferQuantity}p")
            val firstPayment = normalNodeRpcOps.startFlow(::CashPaymentFlow, Amount(transferQuantity, currency), haNodeParty, false).returnValue.getOrThrow()
            logger.info("#$iterNo.2 - Success: normal -> ha, amount: ${transferQuantity}p, TX ID: ${firstPayment.stx.id}")
            allPayments.add(firstPayment)

            val transferBackQuantity = transferQuantity
            logger.info("#$iterNo.3 - Trying: ha -> normal, amount: ${transferBackQuantity}p")
            // TODO: HA node may well have a period of instability, therefore the following RPC posting has to be done in re-try fashion.
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
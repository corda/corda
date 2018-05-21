package net.corda.haTesting

import joptsimple.OptionSet
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.*
import net.corda.finance.GBP
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

        // It is assumed that normal Node is capable of issuing.
        // Create a unique tag for this issuance round
        val issuerBankPartyRef = SecureHash.randomSHA256().bytes
        val currency = GBP
        val amount = Amount(1_000_000, currency)
        logger.info("Trying: issue to normal, amount: $amount")
        val issueOutcome = normalNodeRpcOps.startFlow(::CashIssueFlow, amount, OpaqueBytes(issuerBankPartyRef), notary).returnValue.getOrThrow()
        logger.info("Success: issue to normal, amount: $amount, TX ID: ${issueOutcome.stx.tx.id}")

        // TODO start a daemon thread which will talk to HA Node and installs termination schedule to it
        // The daemon will monitor availability of HA Node and as soon as it is down and then back-up it will install
        // the next termination schedule.

        val iterCount = 10
        val initialAmount: Long = 1000
        require(initialAmount > iterCount)

        for(iterNo in 0 until iterCount) {
            val transferQuantity = initialAmount - iterNo
            logger.info("Trying: normal -> ha, amount: ${transferQuantity}p")
            val firstPayment = normalNodeRpcOps.startFlow(::CashPaymentFlow, Amount(transferQuantity, currency), haNodeParty, true).returnValue.getOrThrow()
            logger.info("Success: normal -> ha, amount: ${transferQuantity}p, TX ID: ${firstPayment.stx.tx.id}")

            logger.info("Trying: ha -> normal, amount: ${transferQuantity - 1}p")
            // TODO: HA node may well have a period of instability, therefore the following RPC posting has to be done in re-try fashion.
            val secondPayment = haNodeRpcOps.startFlow(::CashPaymentFlow, Amount(transferQuantity - 1, currency), normalNodeParty, true).returnValue.getOrThrow()
            logger.info("Success: ha -> normal, amount: ${transferQuantity - 1}p, TX ID: ${secondPayment.stx.tx.id}")
        }

        // TODO: Verify

        // Only then we confirm all the checks have passed.
        return true
    }

}
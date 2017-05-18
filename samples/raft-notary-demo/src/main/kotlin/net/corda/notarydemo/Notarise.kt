package net.corda.notarydemo

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.notUsed
import net.corda.core.crypto.toStringShort
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.BOB
import net.corda.notarydemo.flows.DummyIssueAndMove
import net.corda.notarydemo.flows.RPCStartableNotaryFlowClient

fun main(args: Array<String>) {
    val host = HostAndPort.fromString("localhost:10003")
    println("Connecting to the recipient node ($host)")
    CordaRPCClient(host).start("demo", "demo").use {
        val api = NotaryDemoClientApi(it.proxy)
        api.startNotarisation()
    }
}

/** Interface for using the notary demo API from a client. */
private class NotaryDemoClientApi(val rpc: CordaRPCOps) {
    private val notary by lazy {
        val (parties, partyUpdates) = rpc.networkMapUpdates()
        partyUpdates.notUsed()
        parties.first { it.advertisedServices.any { it.info.type.isNotary() } }.notaryIdentity
    }

    private val counterpartyNode by lazy {
        val (parties, partyUpdates) = rpc.networkMapUpdates()
        partyUpdates.notUsed()
        parties.first { it.legalIdentity.name == BOB.name }
    }

    private companion object {
        private val TRANSACTION_COUNT = 10
    }

    /** Makes calls to the node rpc to start transaction notarisation. */
    fun startNotarisation() {
        notarise(TRANSACTION_COUNT)
    }

    fun notarise(count: Int) {
        val transactions = buildTransactions(count)
        val signers = notariseTransactions(transactions)
        val transactionSigners = transactions.zip(signers).map {
            val (tx, signer) = it
            "Tx [${tx.tx.id.prefixChars()}..] signed by $signer"
        }.joinToString("\n")

        println("Notary: \"${notary.name}\", with composite key: ${notary.owningKey.toStringShort()}\n" +
                "Notarised ${transactions.size} transactions:\n" + transactionSigners)
    }

    /**
     * Builds a number of dummy transactions (as specified by [count]). The party first self-issues a state (asset),
     * and builds a transaction to transfer the asset to the counterparty. The *move* transaction requires notarisation,
     * as it consumes the original asset and creates a copy with the new owner as its output.
     */
    private fun buildTransactions(count: Int): List<SignedTransaction> {
        val moveTransactions = (1..count).map {
            rpc.startFlow(::DummyIssueAndMove, notary, counterpartyNode.legalIdentity).returnValue
        }
        return Futures.allAsList(moveTransactions).getOrThrow()
    }

    /**
     * For every transaction invoke the notary flow and obtains a notary signature.
     * The signer can be any of the nodes in the notary cluster.
     *
     * @return a list of encoded signer public keys - one for every transaction
     */
    private fun notariseTransactions(transactions: List<SignedTransaction>): List<String> {
        // TODO: Remove this suppress when we upgrade to kotlin 1.1 or when JetBrain fixes the bug.
        @Suppress("UNSUPPORTED_FEATURE")
        val signatureFutures = transactions.map { rpc.startFlow(::RPCStartableNotaryFlowClient, it).returnValue }
        return Futures.allAsList(signatureFutures).getOrThrow().map { it.map { it.by.toStringShort() }.joinToString() }
    }
}

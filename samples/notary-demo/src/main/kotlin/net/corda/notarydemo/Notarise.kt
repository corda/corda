package net.corda.notarydemo

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.notUsed
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.toStringShort
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.notarydemo.flows.DummyIssueAndMove
import net.corda.notarydemo.flows.RPCStartableNotaryFlowClient
import net.corda.testing.BOB
import kotlin.streams.asSequence

fun main(args: Array<String>) {
    val address = NetworkHostAndPort("localhost", 10003)
    println("Connecting to the otherSide node ($address)")
    CordaRPCClient(address).start(notaryDemoUser.username, notaryDemoUser.password).use {
        NotaryDemoClientApi(it.proxy).notarise(10)
    }
}

/** Interface for using the notary demo API from a client. */
private class NotaryDemoClientApi(val rpc: CordaRPCOps) {
    private val notary by lazy {
        val parties = rpc.networkMapSnapshot()
        val id = parties.stream().filter { it.advertisedServices.any { it.info.type.isNotary() } }.map { it.notaryIdentity }.distinct().asSequence().singleOrNull()
        checkNotNull(id) { "No unique notary identity, try cleaning the node directories." }
    }

    private val counterpartyNode by lazy {
        val parties = rpc.networkMapSnapshot()
        parties.single { it.legalIdentity.name == BOB.name }
    }

    /** Makes calls to the node rpc to start transaction notarisation. */
    fun notarise(count: Int) {
        println("Notary: \"${notary.name}\", with composite key: ${notary.owningKey.toStringShort()}")
        val transactions = buildTransactions(count)
        println("Notarised ${transactions.size} transactions:")
        transactions.zip(notariseTransactions(transactions)).forEach { (tx, signersFuture) ->
            println("Tx [${tx.tx.id.prefixChars()}..] signed by ${signersFuture.getOrThrow().joinToString()}")
        }
    }

    /**
     * Builds a number of dummy transactions (as specified by [count]). The party first self-issues a state (asset),
     * and builds a transaction to transfer the asset to the counterparty. The *move* transaction requires notarisation,
     * as it consumes the original asset and creates a copy with the new owner as its output.
     */
    private fun buildTransactions(count: Int): List<SignedTransaction> {
        return (1..count).map {
            rpc.startFlow(::DummyIssueAndMove, notary, counterpartyNode.legalIdentity, it).returnValue
        }.transpose().getOrThrow()
    }

    /**
     * For every transaction invoke the notary flow and obtains a notary signature.
     * The signer can be any of the nodes in the notary cluster.
     *
     * @return a list of encoded signer public keys - one for every transaction
     */
    private fun notariseTransactions(transactions: List<SignedTransaction>): List<CordaFuture<List<String>>> {
        return transactions.map {
            rpc.startFlow(::RPCStartableNotaryFlowClient, it).returnValue.map { it.map { it.by.toStringShort() } }
        }
    }
}

/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notarydemo

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.notarydemo.flows.DummyIssueAndMove
import net.corda.notarydemo.flows.RPCStartableNotaryFlowClient
import net.corda.testing.core.BOB_NAME
import java.util.concurrent.Future

fun main(args: Array<String>) {
    val address = NetworkHostAndPort("localhost", 10003)
    println("Connecting to the recipient node ($address)")
    CordaRPCClient(address).start(notaryDemoUser.username, notaryDemoUser.password).use {
        NotaryDemoClientApi(it.proxy).notarise(10)
    }
}

/** Interface for using the notary demo API from a client. */
private class NotaryDemoClientApi(val rpc: CordaRPCOps) {
    private val notary by lazy {
        val id = rpc.notaryIdentities().singleOrNull()
        checkNotNull(id) { "No unique notary identity, try cleaning the node directories." }
    }

    private val counterparty by lazy {
        val parties = rpc.networkMapSnapshot()
        parties.fold(ArrayList<PartyAndCertificate>()) { acc, elem ->
            acc.addAll(elem.legalIdentitiesAndCerts.filter { it.name == BOB_NAME })
            acc
        }.single().party
    }

    /** Makes calls to the node rpc to start transaction notarisation. */
    fun notarise(count: Int) {
        val keyType = if (notary.owningKey is CompositeKey) "composite" else "public"
        println("Notary: \"${notary.name}\", with $keyType key: ${notary.owningKey.toStringShort()}")
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
        val flowFutures = (1..count).map {
            rpc.startFlow(::DummyIssueAndMove, notary, counterparty, it).returnValue
        }
        return flowFutures.map { it.getOrThrow() }
    }

    /**
     * For every transaction invoke the notary flow and obtains a notary signature.
     * The signer can be any of the nodes in the notary cluster.
     *
     * @return a list of encoded signer public keys - one for every transaction
     */
    private fun notariseTransactions(transactions: List<SignedTransaction>): List<Future<List<String>>> {
        return transactions.map {
            rpc.startFlow(::RPCStartableNotaryFlowClient, it).returnValue.toCompletableFuture().thenApply { it.map { it.by.toStringShort() } }
        }
    }
}

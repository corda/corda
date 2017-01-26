package net.corda.notarydemo

import com.google.common.net.HostAndPort
import net.corda.core.crypto.toStringShort
import net.corda.core.div
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotaryFlow
import net.corda.node.services.config.SSLConfiguration
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.notarydemo.flows.DummyIssueAndMove
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
    val host = HostAndPort.fromString("localhost:10002")
    println("Connecting to the recipient node ($host)")
    CordaRPCClient(host, sslConfigFor("nodeb", "build/notary-demo-nodes/Party/certificates")).use("demo", "demo") {
        val api = NotaryDemoClientApi(this)
        api.startNotarisation()
    }
}

/** Interface for using the notary demo API from a client. */
private class NotaryDemoClientApi(val rpc: CordaRPCOps) {

    private val notary by lazy {
        rpc.networkMapUpdates().first.first { it.advertisedServices.any { it.info.type.isNotary() } }.notaryIdentity
    }

    private val counterpartyNode by lazy {
        rpc.networkMapUpdates().first.first { it.legalIdentity.name == "Counterparty" }
    }

    private companion object {
        private val TRANSACTION_COUNT = 10
    }

    /** Makes a call to the demo api to start transaction notarisation. */
    fun startNotarisation() {
        val response = notarise(TRANSACTION_COUNT)
        println(response)
    }

    fun notarise(count: Int): String {
        val transactions = buildTransactions(count)
        val signers = notariseTransactions(transactions)

        return buildResponse(transactions, signers)
    }

    /**
     * Builds a number of dummy transactions (as specified by [count]). The party first self-issues a state (asset),
     * and builds a transaction to transfer the asset to the counterparty. The *move* transaction requires notarisation,
     * as it consumes the original asset and creates a copy with the new owner as its output.
     */
    private fun buildTransactions(count: Int): List<SignedTransaction> {
        val moveTransactions = (1..count).map {
            rpc.startFlow(::DummyIssueAndMove, notary, counterpartyNode.legalIdentity).returnValue.toBlocking().toFuture()
        }
        return moveTransactions.map { it.get() }
    }

    /**
     * For every transaction invoke the notary flow and obtains a notary signature.
     * The signer can be any of the nodes in the notary cluster.
     *
     * @return a list of encoded signer public keys - one for every transaction
     */
    private fun notariseTransactions(transactions: List<SignedTransaction>): List<String> {
        val signatureFutures = transactions.map {
            rpc.startFlow(NotaryFlow::Client, it).returnValue.toBlocking().toFuture()
        }
        val signers = signatureFutures.map { it.get().by.toStringShort() }
        return signers
    }

    /** Builds a response for the caller containing the list of transaction ids and corresponding signer keys. */
    private fun buildResponse(transactions: List<SignedTransaction>, signers: List<String>): String {
        val transactionSigners = transactions.zip(signers).map {
            val (tx, signer) = it
            "Tx [${tx.tx.id.prefixChars()}..] signed by $signer"
        }.joinToString("\n")

        val response = "Notary: \"${notary.name}\", with composite key: ${notary.owningKey}\n" +
                "Notarised ${transactions.size} transactions:\n" + transactionSigners
        return response
    }
}

// TODO: Take this out once we have a dedicated RPC port and allow SSL on it to be optional.
private fun sslConfigFor(nodename: String, certsPath: String?): SSLConfiguration {
    return object : SSLConfiguration {
        override val keyStorePassword: String = "cordacadevpass"
        override val trustStorePassword: String = "trustpass"
        override val certificatesDirectory: Path = if (certsPath != null) Paths.get(certsPath) else Paths.get("build") / "nodes" / nodename / "certificates"
    }
}

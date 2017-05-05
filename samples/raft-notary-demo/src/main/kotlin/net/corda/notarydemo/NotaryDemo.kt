package net.corda.notarydemo

import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.Futures
import joptsimple.OptionParser
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.notUsed
import net.corda.core.crypto.toStringShort
import net.corda.core.div
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotaryFlow
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.notarydemo.flows.DummyIssueAndMove
import org.bouncycastle.asn1.x500.X500Name
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val host = HostAndPort.fromString("localhost:10003")
    println("Connecting to the recipient node ($host)")
    CordaRPCClient(host).use("demo", "demo") {
        val api = NotaryDemoClientApi(this)
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
        parties.first { it.legalIdentity.name == X500Name("CN=Counterparty,O=R3,OU=corda,L=London,C=UK") }
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

        println("Notary: \"${notary.name}\", with composite key: ${notary.owningKey}\n" +
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
        val signatureFutures = transactions.map { rpc.startFlow(NotaryFlow::Client, it).returnValue }
        return Futures.allAsList(signatureFutures).getOrThrow().map { it.map { it.by.toStringShort() }.joinToString() }
    }
}

private fun getCertPath(args: Array<String>): String? {
    val parser = OptionParser()
    val certsPath = parser.accepts("certificates").withRequiredArg()
    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        exitProcess(1)
    }
    return options.valueOf(certsPath)
}

// TODO: Take this out once we have a dedicated RPC port and allow SSL on it to be optional.
private fun sslConfigFor(nodename: String, certsPath: String?): SSLConfiguration {
    return object : SSLConfiguration {
        override val keyStorePassword: String = "cordacadevpass"
        override val trustStorePassword: String = "trustpass"
        override val certificatesDirectory: Path = if (certsPath != null) Paths.get(certsPath) else Paths.get("build") / "nodes" / nodename / "certificates"
    }
}

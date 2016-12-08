package net.corda.notarydemo.api

import net.corda.core.contracts.DummyContract
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.toStringShort
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.ServiceHub
import net.corda.core.node.recordTransactions
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotaryFlow
import net.corda.notarydemo.flows.DummyIssueAndMove
import java.util.*
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.core.Response

@Path("notarydemo")
class NotaryDemoApi(val rpc: CordaRPCOps) {
    private val notary by lazy {
        rpc.networkMapUpdates().first.first { it.advertisedServices.any { it.info.type.isNotary() } }.notaryIdentity
    }

    private val counterpartyNode by lazy {
        rpc.networkMapUpdates().first.first { it.legalIdentity.name == "Counterparty" }
    }

    @GET
    @Path("/notarise/{count}")
    fun notarise(@PathParam("count") count: Int): Response {
        val transactions = buildTransactions(count)
        val signers = notariseTransactions(transactions)

        val response = buildResponse(transactions, signers)
        return Response.ok(response).build()
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
     * For every transactions invokes the notary flow and obtains a notary signature.
     * The signer can be any of the nodes in the notary cluster.
     *
     * @return a list of encoded signer public keys – one for every transaction
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

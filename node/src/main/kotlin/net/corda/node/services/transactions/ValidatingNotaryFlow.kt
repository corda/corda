package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TimeWindow
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotarisationPayload
import net.corda.core.flows.NotaryError
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.notary.NotaryInternalException
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionWithSignatures
import net.corda.core.transactions.WireTransaction
import java.time.Duration

/**
 * A notary commit flow that makes sure a given transaction is valid before committing it. This does mean that the calling
 * party has to reveal the whole transaction history; however, we avoid complex conflict resolution logic where a party
 * has its input states "blocked" by a transaction from another party, and needs to establish whether that transaction was
 * indeed valid.
 */
open class ValidatingNotaryFlow(otherSideSession: FlowSession, service: SinglePartyNotaryService, etaThreshold: Duration = defaultEstimatedWaitTime) : NotaryServiceFlow(otherSideSession, service, etaThreshold) {
    override fun extractParts(requestPayload: NotarisationPayload): TransactionParts {
        val stx = requestPayload.signedTransaction
        val timeWindow: TimeWindow? = if (stx.coreTransaction is WireTransaction) stx.tx.timeWindow else null
        return TransactionParts(stx.id, stx.inputs, timeWindow, stx.notary, stx.references, stx.networkParametersHash)
    }

    /**
     * Fully resolves the received transaction and its dependencies, runs contract verification logic and checks that
     * the transaction in question has all required signatures apart from the notary's.
     */
    @Suspendable
    override fun verifyTransaction(requestPayload: NotarisationPayload) {
        try {
            val stx = requestPayload.signedTransaction
            resolveAndContractVerify(stx)
            verifySignatures(stx)
        } catch (e: Exception) {
            throw NotaryInternalException(NotaryError.TransactionInvalid(e))
        }
    }

    @Suspendable
    private fun resolveAndContractVerify(stx: SignedTransaction) {
        subFlow(ResolveTransactionsFlow(stx, otherSideSession))
        stx.verify(serviceHub, false)
    }

    private fun verifySignatures(stx: SignedTransaction) {
        val transactionWithSignatures = stx.resolveTransactionWithSignatures(serviceHub)
        checkSignatures(transactionWithSignatures)
    }

    private fun checkSignatures(tx: TransactionWithSignatures) {
        tx.verifySignaturesExcept(service.notaryIdentityKey, *service.rotatedKeys.toTypedArray())
    }
}

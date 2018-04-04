package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.*
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.validateRequest
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionWithSignatures
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.unwrap
import java.security.SignatureException

/**
 * A notary commit flow that makes sure a given transaction is valid before committing it. This does mean that the calling
 * party has to reveal the whole transaction history; however, we avoid complex conflict resolution logic where a party
 * has its input states "blocked" by a transaction from another party, and needs to establish whether that transaction was
 * indeed valid.
 */
class ValidatingNotaryFlow(otherSideSession: FlowSession, service: TrustedAuthorityNotaryService) : NotaryFlow.Service(otherSideSession, service) {
    /**
     * Fully resolves the received transaction and its dependencies, runs contract verification logic and checks that
     * the transaction in question has all required signatures apart from the notary's.
     */
    @Suspendable
    override fun receiveAndVerifyTx(): TransactionParts {
        try {
            val stx = receiveTransaction()
            val notary = stx.notary
            checkNotary(notary)
            resolveAndContractVerify(stx)
            verifySignatures(stx)
            val timeWindow: TimeWindow? = if (stx.coreTransaction is WireTransaction) stx.tx.timeWindow else null
            return TransactionParts(stx.id, stx.inputs, timeWindow, notary!!, stx.references)
        } catch (e: Exception) {
            throw when (e) {
                is TransactionVerificationException,
                is SignatureException -> NotaryInternalException(NotaryError.TransactionInvalid(e))
                else -> e
            }
        }
    }

    @Suspendable
    private fun receiveTransaction(): SignedTransaction {
        return otherSideSession.receive<NotarisationPayload>().unwrap {
            val stx = it.signedTransaction
            validateRequest(NotarisationRequest(stx.inputs, stx.id), it.requestSignature)
            stx
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
        try {
            tx.verifySignaturesExcept(service.notaryIdentityKey)
        } catch (e: SignatureException) {
            throw NotaryInternalException(NotaryError.TransactionInvalid(e))
        }
    }
}

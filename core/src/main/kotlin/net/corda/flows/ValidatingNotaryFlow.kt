package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.Party
import net.corda.core.node.services.TimestampChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import java.security.SignatureException

/**
 * A notary commit flow that makes sure a given transaction is valid before committing it. This does mean that the calling
 * party has to reveal the whole transaction history; however, we avoid complex conflict resolution logic where a party
 * has its input states "blocked" by a transaction from another party, and needs to establish whether that transaction was
 * indeed valid.
 */
class ValidatingNotaryFlow(otherSide: Party.Full,
                           timestampChecker: TimestampChecker,
                           uniquenessProvider: UniquenessProvider) :
        NotaryFlow.Service(otherSide, timestampChecker, uniquenessProvider) {

    @Suspendable
    override fun beforeCommit(stx: SignedTransaction) {
        try {
            checkSignatures(stx)
            val wtx = stx.tx
            resolveTransaction(wtx)
            wtx.toLedgerTransaction(serviceHub).verify()
        } catch (e: Exception) {
            when (e) {
                is TransactionVerificationException -> NotaryException(NotaryError.TransactionInvalid(e.toString()))
                is SignatureException -> throw NotaryException(NotaryError.SignaturesInvalid(e.toString()))
                else -> throw e
            }
        }
    }

    private fun checkSignatures(stx: SignedTransaction) {
        try {
            stx.verifySignatures(serviceHub.myInfo.notaryIdentity.owningKey)
        } catch(e: SignedTransaction.SignaturesMissingException) {
            throw NotaryException(NotaryError.SignaturesMissing(e))
        }
    }

    @Suspendable
    private fun resolveTransaction(wtx: WireTransaction) {
        subFlow(ResolveTransactionsFlow(wtx, otherSide))
    }
}

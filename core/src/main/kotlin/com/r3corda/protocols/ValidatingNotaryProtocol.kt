package com.r3corda.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.contracts.TransactionVerificationException
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.services.TimestampChecker
import com.r3corda.core.node.services.UniquenessProvider
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.transactions.WireTransaction
import java.security.SignatureException

/**
 * A notary commit protocol that makes sure a given transaction is valid before committing it. This does mean that the calling
 * party has to reveal the whole transaction history; however, we avoid complex conflict resolution logic where a party
 * has its input states "blocked" by a transaction from another party, and needs to establish whether that transaction was
 * indeed valid.
 */
class ValidatingNotaryProtocol(otherSide: Party,
                               sessionIdForSend: Long,
                               sessionIdForReceive: Long,
                               timestampChecker: TimestampChecker,
                               uniquenessProvider: UniquenessProvider) : NotaryProtocol.Service(otherSide, sessionIdForSend, sessionIdForReceive, timestampChecker, uniquenessProvider) {
    @Suspendable
    override fun beforeCommit(stx: SignedTransaction, reqIdentity: Party) {
        try {
            checkSignatures(stx)
            val wtx = stx.tx
            resolveTransaction(reqIdentity, wtx)
            wtx.toLedgerTransaction(serviceHub).verify()
        } catch (e: Exception) {
            when (e) {
                is TransactionVerificationException,
                is SignatureException -> throw NotaryException(NotaryError.TransactionInvalid())
                else -> throw e
            }
        }
    }

    private fun checkSignatures(stx: SignedTransaction) {
        try {
            stx.verifySignatures(serviceHub.storageService.myLegalIdentity.owningKey)
        } catch(e: SignedTransaction.SignaturesMissingException) {
            throw NotaryException(NotaryError.SignaturesMissing(e.missing))
        }
    }

    @Suspendable
    private fun resolveTransaction(reqIdentity: Party, wtx: WireTransaction) {
        subProtocol(ResolveTransactionsProtocol(wtx, reqIdentity))
    }
}
package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.identity.Party
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.*
import java.security.SignatureException

/**
 * A notary commit flow that makes sure a given transaction is valid before committing it. This does mean that the calling
 * party has to reveal the whole transaction history; however, we avoid complex conflict resolution logic where a party
 * has its input states "blocked" by a transaction from another party, and needs to establish whether that transaction was
 * indeed valid.
 */
class ValidatingNotaryFlow(otherSide: Party, service: TrustedAuthorityNotaryService) : NotaryFlow.Service(otherSide, service) {
    /**
     * The received transaction is checked for contract-validity, which requires fully resolving it into a
     * [TransactionForVerification], for which the caller also has to to reveal the whole transaction
     * dependency chain.
     */
    @Suspendable
    override fun receiveAndVerifyTx(): TransactionParts {
        val stx = receive<SignedTransaction>(otherSide).unwrap { it }
        checkSignatures(stx)
        val wtx = stx.tx
        validateTransaction(wtx)
        return TransactionParts(wtx.id, wtx.inputs, wtx.timeWindow)
    }

    private fun checkSignatures(stx: SignedTransaction) {
        try {
            stx.verifySignatures(serviceHub.myInfo.notaryIdentity.owningKey)
        } catch(e: SignedTransaction.SignaturesMissingException) {
            throw NotaryException(NotaryError.SignaturesMissing(e))
        }
    }

    @Suspendable
    fun validateTransaction(wtx: WireTransaction) {
        try {
            resolveTransaction(wtx)
            wtx.toLedgerTransaction(serviceHub).verify()
        } catch (e: Exception) {
            throw when (e) {
                is TransactionVerificationException -> NotaryException(NotaryError.TransactionInvalid(e.toString()))
                is SignatureException -> NotaryException(NotaryError.SignaturesInvalid(e.toString()))
                else -> e
            }
        }
    }

    @Suspendable
    private fun resolveTransaction(wtx: WireTransaction) = subFlow(ResolveTransactionsFlow(wtx, otherSide))
}

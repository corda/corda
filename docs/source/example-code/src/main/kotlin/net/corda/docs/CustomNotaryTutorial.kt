package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.unwrap
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.node.services.transactions.ValidatingNotaryService
import java.security.SignatureException

// START 1
@CordaService
class MyCustomValidatingNotaryService(override val services: PluginServiceHub) : TrustedAuthorityNotaryService() {
    companion object {
        val type = ValidatingNotaryService.type.getSubType("mycustom")
    }

    override val timeWindowChecker = TimeWindowChecker(services.clock)
    override val uniquenessProvider = PersistentUniquenessProvider()

    override fun createServiceFlow(otherParty: Party, platformVersion: Int): FlowLogic<Void?> {
        return MyValidatingNotaryFlow(otherParty, this)
    }

    override fun start() {}
    override fun stop() {}
}
// END 1

// START 2
class MyValidatingNotaryFlow(otherSide: Party, service: MyCustomValidatingNotaryService) : NotaryFlow.Service(otherSide, service) {
    /**
     * The received transaction is checked for contract-validity, which requires fully resolving it into a
     * [TransactionForVerification], for which the caller also has to to reveal the whole transaction
     * dependency chain.
     */
    @Suspendable
    override fun receiveAndVerifyTx(): TransactionParts {
        val stx = receive<SignedTransaction>(otherSide).unwrap { it }
        checkSignatures(stx)
        resolveTransaction(stx)
        validateTransaction(stx)
        val wtx = stx.tx
        return TransactionParts(wtx.id, wtx.inputs, wtx.timeWindow)
    }

    fun processTransaction(stx: SignedTransaction) {
        // Add custom transaction processing logic here
    }

    private fun checkSignatures(stx: SignedTransaction) {
        try {
            stx.verifySignaturesExcept(serviceHub.myInfo.notaryIdentity.owningKey)
        } catch(e: SignatureException) {
            throw NotaryException(NotaryError.TransactionInvalid(e))
        }
    }

    @Suspendable
    fun validateTransaction(stx: SignedTransaction) {
        try {
            resolveTransaction(stx)
            stx.verify(serviceHub, false)
        } catch (e: Exception) {
            throw when (e) {
                is TransactionVerificationException,
                is SignatureException -> NotaryException(NotaryError.TransactionInvalid(e))
                else -> e
            }
        }
    }

    @Suspendable
    private fun resolveTransaction(stx: SignedTransaction) = subFlow(ResolveTransactionsFlow(stx, otherSide))
}
// END 2

package net.corda.notarydemo

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.*
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.validateRequest
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionWithSignatures
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.unwrap
import net.corda.node.services.transactions.PersistentUniquenessProvider
import java.security.PublicKey
import java.security.SignatureException

/**
 * A custom notary service should provide a constructor that accepts two parameters of types [AppServiceHub] and [PublicKey].
 *
 * Note that the support for custom notaries is still experimental – at present only a single-node notary service can be customised.
 * The notary-related APIs might change in the future.
 */
// START 1
@CordaService
class MyCustomValidatingNotaryService(override val services: AppServiceHub, override val notaryIdentityKey: PublicKey) : TrustedAuthorityNotaryService() {
    override val uniquenessProvider = PersistentUniquenessProvider()

    override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = MyValidatingNotaryFlow(otherPartySession, this)

    override fun start() {}
    override fun stop() {}
}
// END 1

@Suppress("UNUSED_PARAMETER")
// START 2
class MyValidatingNotaryFlow(otherSide: FlowSession, service: MyCustomValidatingNotaryService) : NotaryFlow.Service(otherSide, service) {
    /**
     * The received transaction is checked for contract-validity, for which the caller also has to to reveal the whole
     * transaction dependency chain.
     */
    @Suspendable
    override fun receiveAndVerifyTx(): TransactionParts {
        try {
            val stx = receiveTransaction()
            val notary = stx.notary
            checkNotary(notary)
            verifySignatures(stx)
            resolveAndContractVerify(stx)
            val timeWindow: TimeWindow? = if (stx.coreTransaction is WireTransaction) stx.tx.timeWindow else null
            return TransactionParts(stx.id, stx.inputs, timeWindow, notary!!, stx.unspendableInputs)
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
        customVerify(stx)
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

    private fun customVerify(stx: SignedTransaction) {
        // Add custom verification logic
    }
}
// END 2

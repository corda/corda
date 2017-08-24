package net.corda.core.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.AttachmentResolutionException
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import java.security.SignatureException

/**
 * The [ReceiveTransactionFlow] should be called in response to the [SendTransactionFlow].
 *
 * This flow is a combination of [receive], resolve and [SignedTransaction.verify]. This flow will receive the [SignedTransaction]
 * and perform the resolution back-and-forth required to check the dependencies and download any missing attachments.
 * The flow will return the [SignedTransaction] after it is resolved and then verified using [SignedTransaction.verify].
 */
class ReceiveTransactionFlow
@JvmOverloads
constructor(private val otherParty: Party, private val checkSufficientSignatures: Boolean = true) : FlowLogic<SignedTransaction>() {
    @Suspendable
    @Throws(SignatureException::class, AttachmentResolutionException::class, TransactionResolutionException::class, TransactionVerificationException::class)
    override fun call(): SignedTransaction {
        return receive<SignedTransaction>(otherParty).unwrap {
            subFlow(net.corda.core.internal.ResolveTransactionsFlow(it, otherParty))
            it.verify(serviceHub, checkSufficientSignatures)
            it
        }
    }
}


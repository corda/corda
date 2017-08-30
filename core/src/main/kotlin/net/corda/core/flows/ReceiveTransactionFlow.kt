package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.type.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
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
            subFlow(ResolveTransactionsFlow(it, otherParty))
            it.verify(serviceHub, checkSufficientSignatures)
            it
        }
    }
}

/**
 * The [ReceiveStateAndRefFlow] should be called in response to the [SendStateAndRefFlow].
 *
 * This flow is a combination of [receive] and resolve. This flow will receive a list of [StateAndRef]
 * and perform the resolution back-and-forth required to check the dependencies.
 * The flow will return the list of [StateAndRef] after it is resolved.
 */
// @JvmSuppressWildcards is used to suppress wildcards in return type when calling `subFlow(new ReceiveStateAndRef<T>(otherParty))` in java.
class ReceiveStateAndRefFlow<out T : ContractState>(private val otherParty: Party) : FlowLogic<@JvmSuppressWildcards List<StateAndRef<T>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<T>> {
        return receive<List<StateAndRef<T>>>(otherParty).unwrap {
            subFlow(ResolveTransactionsFlow(it.map { it.ref.txhash }.toSet(), otherParty))
            it
        }
    }
}

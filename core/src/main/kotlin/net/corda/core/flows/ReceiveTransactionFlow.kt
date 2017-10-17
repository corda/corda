package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import java.security.SignatureException

/**
 * The [ReceiveTransactionFlow] should be called in response to the [SendTransactionFlow].
 *
 * This flow is a combination of [FlowSession.receive], resolve and [SignedTransaction.verify]. This flow will receive the
 * [SignedTransaction] and perform the resolution back-and-forth required to check the dependencies and download any missing
 * attachments. The flow will return the [SignedTransaction] after it is resolved and then verified using [SignedTransaction.verify].
 *
 * @param otherSideSession session to the other side which is calling [SendTransactionFlow].
 * @param checkSufficientSignatures if true checks all required signatures are present. See [SignedTransaction.verify].
 */
class ReceiveTransactionFlow(private val otherSideSession: FlowSession,
                             private val checkSufficientSignatures: Boolean) : FlowLogic<SignedTransaction>() {
    /** Receives a [SignedTransaction] from [otherSideSession], verifies it and then records it in the vault. */
    constructor(otherSideSession: FlowSession) : this(otherSideSession, true)

    @Suspendable
    @Throws(SignatureException::class,
            AttachmentResolutionException::class,
            TransactionResolutionException::class,
            TransactionVerificationException::class)
    override fun call(): SignedTransaction {
        return otherSideSession.receive<SignedTransaction>().unwrap {
            subFlow(ResolveTransactionsFlow(it, otherSideSession))
            it.verify(serviceHub, checkSufficientSignatures)
            it
        }
    }
}

/**
 * The [ReceiveStateAndRefFlow] should be called in response to the [SendStateAndRefFlow].
 *
 * This flow is a combination of [FlowSession.receive] and resolve. This flow will receive a list of [StateAndRef]
 * and perform the resolution back-and-forth required to check the dependencies.
 * The flow will return the list of [StateAndRef] after it is resolved.
 */
// @JvmSuppressWildcards is used to suppress wildcards in return type when calling `subFlow(new ReceiveStateAndRef<T>(otherParty))` in java.
class ReceiveStateAndRefFlow<out T : ContractState>(private val otherSideSession: FlowSession) : FlowLogic<@JvmSuppressWildcards List<StateAndRef<T>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<T>> {
        return otherSideSession.receive<List<StateAndRef<T>>>().unwrap {
            subFlow(ResolveTransactionsFlow(it.map { it.ref.txhash }.toSet(), otherSideSession))
            it
        }
    }
}

package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * The [ReceiveTransactionFlow] should be called in response to the [SendTransactionFlow].
 *
 * This flow is a combination of [receive] and [ResolveTransactionsFlow]. This flow will receive the [SignedTransaction]
 * and perform the resolution back-and-forth required to check the dependencies and download any missing attachments.
 * The flow will return the [SignedTransaction] after it is resolved and verified.
 */
class ReceiveTransactionFlow
@JvmOverloads
constructor(private val otherParty: Party, private val checkSufficientSignatures: Boolean = true) : FlowLogic<SignedTransaction>() {
    @Suspendable
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
 * This flow is a combination of [receive] and [ResolveTransactionsFlow]. This flow will receive a list of [StateAndRef]
 * and perform the resolution back-and-forth required to check the dependencies.
 * The flow will return the list of [StateAndRef] after it is resolved.
 *
 * @JvmSuppressWildcards is used to suppress wildcards in return type when calling `subFlow(new ReceiveStateAndRef<T>(otherParty))` in java.
 */
class ReceiveStateAndRefFlow<out T : ContractState>(private val otherParty: Party) : FlowLogic<@JvmSuppressWildcards List<StateAndRef<T>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<T>> {
        return receive<List<StateAndRef<T>>>(otherParty).unwrap {
            subFlow(ResolveTransactionsFlow(it.map { it.ref.txhash }.toSet(), otherParty))
            it
        }
    }
}

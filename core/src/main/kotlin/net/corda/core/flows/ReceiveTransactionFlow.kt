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
 * This flow is a combination of [receive] and [ResolveTransactionsFlow], the flow is expecting a [SignedTransaction]
 * from the [otherParty]. This flow will resolve the [SignedTransaction] and return the [SignedTransaction] after it is resolved.
 */
class ReceiveTransactionFlow(private val otherParty: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        return receive<SignedTransaction>(otherParty).unwrap {
            subFlow(ResolveTransactionsFlow(it, otherParty))
            it
        }
    }
}

/**
 * The [ReceiveStateAndRefFlow] should be called in response to the [SendStateAndRefFlow].
 *
 * This flow is a combination of [receive] and [ResolveTransactionsFlow], the flow is expecting a list of [StateAndRef] from
 * the [otherParty]. This flow will resolve the list of [StateAndRef] and return the list of [StateAndRef] after it is resolved.
 */
class ReceiveStateAndRefFlow<out T : ContractState>(private val otherParty: Party) : FlowLogic<List<StateAndRef<T>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<T>> {
        return receive<List<StateAndRef<T>>>(otherParty).unwrap {
            subFlow(ResolveTransactionsFlow(it.map { it.ref.txhash }.toSet(), otherParty))
            it
        }
    }
}

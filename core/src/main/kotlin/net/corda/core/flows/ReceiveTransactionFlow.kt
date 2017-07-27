package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.unwrap

/**
 * The [ReceiveTransactionFlow] should be called in response to the [SendTransactionFlow].
 *
 * This flow is a combination of [receive] and [ResolveTransactionsFlow], the flow is expecting a [SignedTransaction]
 * from the [otherParty]. This flow will resolve the [SignedTransaction] and return the [SignedTransaction] after its resolved.
 */
class ReceiveTransactionFlow
@JvmOverloads
constructor(private val otherParty: Party,
            private val verifySignatures: Boolean = true,
            private val verifyTransaction: Boolean = true) : FlowLogic<SignedTransaction>() {

    @Suspendable
    @SuppressWarnings
    override fun call(): SignedTransaction {
        return receive<SignedTransaction>(otherParty).unwrap {
            subFlow(ResolveTransactionsFlow(otherParty, it, verifySignatures, verifyTransaction))
            it
        }
    }
}

/**
 * The [ReceiveProposalFlow] should be called in response to the [SendProposalFlow].
 *
 * This flow is a combination of [receive] and [ResolveTransactionsFlow], the flow is expecting a [TradeProposal] from
 * the [otherParty]. This flow will resolve the [TradeProposal.inputStates] and return a [UntrustworthyData] for
 * further verification.
 */
class ReceiveProposalFlow<out T : TradeProposal<*>>(private val expectedClass: Class<T>, private val otherParty: Party) : FlowLogic<UntrustworthyData<T>>() {
    @Suspendable
    @SuppressWarnings
    override fun call(): UntrustworthyData<T> {
        return receive(expectedClass, otherParty).unwrap {
            subFlow(ResolveTransactionsFlow(otherParty, it.inputStates.map { it.ref.txhash }.toSet()))
            UntrustworthyData(it)
        }
    }
}


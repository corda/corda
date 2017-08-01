package net.corda.flows

import net.corda.core.contracts.*
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * A flow to be used for changing a state's Notary. This is required since all input states to a transaction
 * must point to the same notary.
 *
 * This assembles the transaction for notary replacement and sends out change proposals to all participants
 * of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, the transaction containing all signatures is sent back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
@InitiatingFlow
class NotaryChangeFlow<out T : ContractState>(
        originalState: StateAndRef<T>,
        newNotary: Party,
        progressTracker: ProgressTracker = tracker())
    : AbstractStateReplacementFlow.Instigator<T, T, Party>(originalState, newNotary, progressTracker) {

    override fun assembleTx(): AbstractStateReplacementFlow.UpgradeTx {
        val state = originalState.state
        val tx = TransactionType.NotaryChange.Builder(originalState.state.notary)

        val participants: Iterable<AbstractParty> = if (state.encumbrance == null) {
            val modifiedState = TransactionState(state.data, modification)
            tx.addInputState(originalState)
            tx.addOutputState(modifiedState)
            state.data.participants
        } else {
            resolveEncumbrances(tx)
        }

        val stx = serviceHub.signInitialTransaction(tx)
        val participantKeys = participants.map { it.owningKey }
        // TODO: We need a much faster way of finding our key in the transaction
        val myKey = serviceHub.keyManagementService.filterMyKeys(participantKeys).single()

        return AbstractStateReplacementFlow.UpgradeTx(stx, participantKeys, myKey)
    }

    /**
     * Adds the notary change state transitions to the [tx] builder for the [originalState] and its encumbrance
     * state chain (encumbrance states might be themselves encumbered by other states).
     *
     * @return union of all added states' participants
     */
    private fun resolveEncumbrances(tx: TransactionBuilder): Iterable<AbstractParty> {
        val stateRef = originalState.ref
        val txId = stateRef.txhash
        val issuingTx = serviceHub.validatedTransactions.getTransaction(txId)
                ?: throw StateReplacementException("Transaction $txId not found")
        val outputs = issuingTx.tx.outputs

        val participants = mutableSetOf<AbstractParty>()

        var nextStateIndex = stateRef.index
        var newOutputPosition = tx.outputStates().size
        while (true) {
            val nextState = outputs[nextStateIndex]
            tx.addInputState(StateAndRef(nextState, StateRef(txId, nextStateIndex)))
            participants.addAll(nextState.data.participants)

            if (nextState.encumbrance == null) {
                val modifiedState = TransactionState(nextState.data, modification)
                tx.addOutputState(modifiedState)
                break
            } else {
                val modifiedState = TransactionState(nextState.data, modification, newOutputPosition + 1)
                tx.addOutputState(modifiedState)
                nextStateIndex = nextState.encumbrance
            }

            newOutputPosition++
        }

        return participants
    }
}

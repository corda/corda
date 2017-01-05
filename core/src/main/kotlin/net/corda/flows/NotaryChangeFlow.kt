package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.flows.NotaryChangeFlow.Acceptor
import net.corda.flows.NotaryChangeFlow.Instigator

/**
 * A flow to be used for changing a state's Notary. This is required since all input states to a transaction
 * must point to the same notary.
 *
 * The [Instigator] assembles the transaction for notary replacement and sends out change proposals to all participants
 * ([Acceptor]) of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, [Instigator] sends the transaction containing all signatures back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
object NotaryChangeFlow : AbstractStateReplacementFlow<Party>() {

    data class Proposal(override val stateRef: StateRef,
                        override val modification: Party,
                        override val stx: SignedTransaction) : AbstractStateReplacementFlow.Proposal<Party>

    class Instigator<T : ContractState>(originalState: StateAndRef<T>,
                                        newNotary: Party,
                                        progressTracker: ProgressTracker = tracker())
        : AbstractStateReplacementFlow.Instigator<T, Party>(originalState, newNotary, progressTracker) {

        override fun assembleProposal(stateRef: StateRef, modification: Party, stx: SignedTransaction): AbstractStateReplacementFlow.Proposal<Party>
                = Proposal(stateRef, modification, stx)

        override fun assembleTx(): Pair<SignedTransaction, Iterable<CompositeKey>> {
            val state = originalState.state
            val tx = TransactionType.NotaryChange.Builder(originalState.state.notary)

            val participants: Iterable<CompositeKey>

            if (state.encumbrance == null) {
                val modifiedState = TransactionState(state.data, modification)
                tx.addInputState(originalState)
                tx.addOutputState(modifiedState)
                participants = state.data.participants
            } else {
                participants = resolveEncumbrances(tx)
            }

            val myKey = serviceHub.legalIdentityKey
            tx.signWith(myKey)

            val stx = tx.toSignedTransaction(false)

            return Pair(stx, participants)
        }

        /**
         * Adds the notary change state transitions to the [tx] builder for the [originalState] and its encumbrance
         * state chain (encumbrance states might be themselves encumbered by other states).
         *
         * @return union of all added states' participants
         */
        private fun resolveEncumbrances(tx: TransactionBuilder): Iterable<CompositeKey> {
            val stateRef = originalState.ref
            val txId = stateRef.txhash
            val issuingTx = serviceHub.storageService.validatedTransactions.getTransaction(txId) ?: throw IllegalStateException("Transaction $txId not found")
            val outputs = issuingTx.tx.outputs

            val participants = mutableSetOf<CompositeKey>()

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

    class Acceptor(otherSide: Party,
                   override val progressTracker: ProgressTracker = tracker())
        : AbstractStateReplacementFlow.Acceptor<Party>(otherSide) {

        /**
         * Check the notary change proposal.
         *
         * For example, if the proposed new notary has the same behaviour (e.g. both are non-validating)
         * and is also in a geographically convenient location we can just automatically approve the change.
         * TODO: In more difficult cases this should call for human attention to manually verify and approve the proposal
         */
        @Suspendable
        override fun verifyProposal(maybeProposal: UntrustworthyData<AbstractStateReplacementFlow.Proposal<Party>>): AbstractStateReplacementFlow.Proposal<Party> {
            return maybeProposal.unwrap { proposal ->
                val newNotary = proposal.modification
                val isNotary = serviceHub.networkMapCache.notaryNodes.any { it.notaryIdentity == newNotary }
                require(isNotary) { "The proposed node $newNotary does not run a Notary service " }

                val state = proposal.stateRef
                val proposedTx = proposal.stx.tx
                require(state in proposedTx.inputs) { "The proposed state $state is not in the proposed transaction inputs" }
                require(proposedTx.type.javaClass == TransactionType.NotaryChange::class.java) {
                    "The proposed transaction is not a notary change transaction."
                }

                // An example requirement
                val blacklist = listOf("Evil Notary")
                require(!blacklist.contains(newNotary.name)) { "The proposed new notary $newNotary is not trusted by the party" }

                proposal
            }
        }
    }
}

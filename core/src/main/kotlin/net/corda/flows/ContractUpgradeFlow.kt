package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.flows.ContractUpgradeFlow.Acceptor
import net.corda.flows.ContractUpgradeFlow.Instigator

/**
 * A flow to be used for upgrading state objects of an old contract to a new contract.
 *
 * The [Instigator] assembles the transaction for contract replacement and sends out change proposals to all participants
 * ([Acceptor]) of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, [Instigator] sends the transaction containing all signatures back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
object ContractUpgradeFlow : AbstractStateReplacementFlow<UpgradedContract<*>>() {
    val TOPIC = "platform.contract_upgrade"

    data class Proposal<S : ContractState>(override val stateRef: StateRef,
                                           override val modification: UpgradedContract<S>,
                                           override val stx: SignedTransaction) : AbstractStateReplacementFlow.Proposal<UpgradedContract<S>>

    internal fun <T : ContractState> assembleBareTx(stateRef: StateAndRef<T>,
                                                    newContract: UpgradedContract<T>): TransactionBuilder {
        return TransactionType.General.Builder(stateRef.state.notary).withItems(stateRef, newContract.upgrade(stateRef.state.data))
    }

    class Instigator<S : ContractState>(originalState: StateAndRef<S>,
                                        newContract: UpgradedContract<S>,
                                        progressTracker: ProgressTracker = tracker())
    : AbstractStateReplacementFlow.Instigator<S, UpgradedContract<S>>(originalState, newContract, progressTracker) {
        override fun assembleProposal(stateRef: StateRef, modification: UpgradedContract<S>, stx: SignedTransaction)
                = Proposal<S>(stateRef, modification, stx)

        override fun assembleTx(): Pair<SignedTransaction, List<CompositeKey>> {
            val ptx = assembleBareTx(originalState, modification)
            ptx.signWith(serviceHub.legalIdentityKey)
            return Pair(ptx.toSignedTransaction(false), originalState.state.data.participants)
        }
    }

    class Acceptor<T : ContractState>(otherSide: Party,
                                      val clazz: Class<T>,
                                      override val progressTracker: ProgressTracker = tracker())
    : AbstractStateReplacementFlow.Acceptor<UpgradedContract<T>>(otherSide) {
        @Suspendable
        override fun verifyProposal(maybeProposal: UntrustworthyData<AbstractStateReplacementFlow.Proposal<UpgradedContract<T>>>): AbstractStateReplacementFlow.Proposal<UpgradedContract<T>> {
            return maybeProposal.unwrap { proposal ->
                val states = serviceHub.vaultService.statesForRefs(listOf(proposal.stateRef))
                val state = states[proposal.stateRef] ?: throw IllegalStateException("We don't have a copy of the referenced state")

                require (state.data.javaClass.equals(clazz))

                @Suppress("unchecked_cast")
                // We've enforced type safety above, just we can't do it in a way Kotlin recognises
                val stateAndRef = StateAndRef<T>(state as TransactionState<T>, proposal.stateRef)
                val upgradeCandidates: Set<UpgradedContract<T>> = serviceHub.vaultService.getUpgradeCandidates<T>(state.data.contract)
                val actualTx = proposal.stx.tx
                val expectedTx = assembleBareTx(stateAndRef, proposal.modification).toWireTransaction()
                requireThat {
                    "the proposed contract $proposal.contract is a trusted upgrade path" by (proposal.modification in upgradeCandidates)
                    "the proposed tx matches the expected tx for this upgrade" by (actualTx == expectedTx)
                }

                proposal
            }
        }
    }
}

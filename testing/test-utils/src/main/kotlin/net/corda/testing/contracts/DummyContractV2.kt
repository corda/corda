package net.corda.testing.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder

// The dummy contract doesn't do anything useful. It exists for testing purposes.

/**
 * Dummy contract state for testing of the upgrade process.
 */
// DOCSTART 1
class DummyContractV2 : UpgradedContractWithLegacyConstraint<DummyContract.State, DummyContractV2.State> {
    companion object {
        const val PROGRAM_ID: ContractClassName = "net.corda.testing.contracts.DummyContractV2"


        /**
         * An overload of move for just one input state.
         */
        @JvmStatic
        fun move(prior: StateAndRef<State>, newOwner: AbstractParty) = move(listOf(prior), newOwner)

        /**
         * Returns a [TransactionBuilder] that takes the given input states and transfers them to the newOwner.
         */
        @JvmStatic
        fun move(priors: List<StateAndRef<State>>, newOwner: AbstractParty): TransactionBuilder {
            require(priors.isNotEmpty()){"States to move to new owner must not be empty"}
            val priorState = priors[0].state.data
            val (cmd, state) = priorState.withNewOwner(newOwner)
            return TransactionBuilder(notary = priors[0].state.notary).withItems(
                    /* INPUTS  */ *priors.toTypedArray(),
                    /* COMMAND */ Command(cmd, priorState.owners.map { it.owningKey }),
                    /* OUTPUT  */ StateAndContract(state, DummyContractV2.PROGRAM_ID)
            )
        }
    }

    override val legacyContract: String = DummyContract::class.java.name
    override val legacyContractConstraint: AttachmentConstraint = AlwaysAcceptAttachmentConstraint

    data class State(val magicNumber: Int = 0, val owners: List<AbstractParty>) : ContractState {
        override val participants: List<AbstractParty> = owners

        fun withNewOwner(newOwner: AbstractParty): Pair<Commands, State> {
            val newState = this.copy(owners = listOf(newOwner))
            return Pair(Commands.Move(), newState)
        }
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    override fun upgrade(state: DummyContract.State): State {
        return State(state.magicNumber, state.participants)
    }

    override fun verify(tx: LedgerTransaction) {
        // Other verifications.
    }
}
// DOCEND 1

package net.corda.testing.contracts

import net.corda.core.DoNotImplement
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder

// The dummy contract doesn't do anything useful. It exists for testing purposes, but has to be serializable

/**
 * Dummy contract for testing purposes. Doesn't do anything useful.
 */
data class DummyContract(val blank: Any? = null) : Contract {

    val PROGRAM_ID = "net.corda.testing.contracts.DummyContract"

    @DoNotImplement // State is effectively a sealed class.
    interface State : ContractState {
        /** Some information that the state represents for test purposes. **/
        val magicNumber: Int
    }

    data class SingleOwnerState(override val magicNumber: Int = 0, override val owner: AbstractParty) : OwnableState, State {
        override val participants: List<AbstractParty>
            get() = listOf(owner)

        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(), copy(owner = newOwner))
    }

    /**
     * Alternative state with multiple owners. This exists primarily to provide a dummy state with multiple
     * participants, and could in theory be merged with [SingleOwnerState] by putting the additional participants
     * in a different field, however this is a good example of a contract with multiple states.
     */
    data class MultiOwnerState(override val magicNumber: Int = 0,
                               val owners: List<AbstractParty>) : State {
        override val participants: List<AbstractParty> get() = owners
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        // Always accepts.
    }

    companion object {
        const val PROGRAM_ID: ContractClassName = "net.corda.testing.contracts.DummyContract"

        /**
         * Returns a [TransactionBuilder] with the given notary, a list of owners and an output state of type
         * [SingleOwnerState] or [MultipleOwnerState] (depending on the number of owner parameters passed) containing
         * the given magicNumber.
         */
        @JvmStatic
        fun generateInitial(magicNumber: Int, notary: Party, owner: PartyAndReference, vararg otherOwners: PartyAndReference): TransactionBuilder {
            val owners = listOf(owner) + otherOwners
            return if (owners.size == 1) {
                val state = SingleOwnerState(magicNumber, owners.first().party)
                TransactionBuilder(notary).withItems(StateAndContract(state, PROGRAM_ID), Command(Commands.Create(), owners.first().party.owningKey))
            } else {
                val state = MultiOwnerState(magicNumber, owners.map { it.party })
                TransactionBuilder(notary).withItems(StateAndContract(state, PROGRAM_ID), Command(Commands.Create(), owners.map { it.party.owningKey }))
            }
        }

        /**
         * An overload of move for just one input state.
         */
        @JvmStatic
        fun move(prior: StateAndRef<SingleOwnerState>, newOwner: AbstractParty) = move(listOf(prior), newOwner)

        /**
         * Returns a [TransactionBuilder] that takes the given input states and transfers them to the newOwner.
         */
        @JvmStatic
        fun move(priors: List<StateAndRef<SingleOwnerState>>, newOwner: AbstractParty): TransactionBuilder {
            require(priors.isNotEmpty())
            val priorState = priors[0].state.data
            val (cmd, state) = priorState.withNewOwner(newOwner)
            return TransactionBuilder(notary = priors[0].state.notary).withItems(
                    /* INPUTS  */ *priors.toTypedArray(),
                    /* COMMAND */ Command(cmd, priorState.owner.owningKey),
                    /* OUTPUT  */ StateAndContract(state, PROGRAM_ID)
            )
        }
    }
}

package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val DUMMY_PROGRAM_ID = DummyContract()

class DummyContract : Contract {
    data class State(val magicNumber: Int = 0) : ContractState {
        override val contract = DUMMY_PROGRAM_ID
        override val participants: List<PublicKey>
            get() = emptyList()
    }

    data class SingleOwnerState(val magicNumber: Int = 0, override val owner: PublicKey) : OwnableState {
        override val contract = DUMMY_PROGRAM_ID
        override val participants: List<PublicKey>
            get() = listOf(owner)

        override fun withNewOwner(newOwner: PublicKey) = Pair(Commands.Move(), copy(owner = newOwner))
    }

    data class MultiOwnerState(val magicNumber: Int = 0,
                               val owners: List<PublicKey>) : ContractState {
        override val contract = DUMMY_PROGRAM_ID
        override val participants: List<PublicKey>
            get() = owners
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForContract) {
        // Always accepts.
    }

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("")

    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder {
        val state = SingleOwnerState(magicNumber, owner.party.owningKey)
        return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.Create(), owner.party.owningKey))
    }
}
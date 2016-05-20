package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val DUMMY_PROGRAM_ID = DummyContract()

class DummyContract : Contract {
    data class State(val magicNumber: Int = 0,
                     override val notary: Party) : ContractState {
        override val contract = DUMMY_PROGRAM_ID
        override val participants: List<PublicKey>
            get() = emptyList()

        override fun withNewNotary(newNotary: Party) = copy(notary = newNotary)
    }

    data class MultiOwnerState(val magicNumber: Int = 0,
                               val owners: List<PublicKey>,
                               override val notary: Party) : ContractState {
        override val contract = DUMMY_PROGRAM_ID
        override val participants: List<PublicKey>
            get() = owners

        override fun withNewNotary(newNotary: Party) = copy(notary = newNotary)
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForVerification) {
        // Always accepts.
    }

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("")

    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder {
        val state = State(magicNumber, notary)
        return TransactionBuilder().withItems(state, Command(Commands.Create(), owner.party.owningKey))
    }
}
package net.corda.testing.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

// The dummy contract doesn't do anything useful. It exists for testing purposes.

/**
 * Dummy contract state for testing of the upgrade process.
 */
class DummyContractV4 : UpgradedContractWithLegacyConstraint<DummyContract.State, DummyContractV4.State> {
    companion object {
        const val PROGRAM_ID: ContractClassName = "net.corda.testing.contracts.DummyContractV4"
    }

    override val legacyContract: String = DummyContract.PROGRAM_ID
    override val legacyContractConstraint: AttachmentConstraint = WhitelistedByZoneAttachmentConstraint

    data class State(val magicNumber: Int = 0, val owners: List<AbstractParty>) : ContractState {
        override val participants: List<AbstractParty> = owners
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

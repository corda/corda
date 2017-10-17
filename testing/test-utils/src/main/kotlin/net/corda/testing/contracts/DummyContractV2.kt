package net.corda.testing.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.UpgradeCommand
import net.corda.core.node.ServicesForResolution
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction

// The dummy contract doesn't do anything useful. It exists for testing purposes.

/**
 * Dummy contract state for testing of the upgrade process.
 */
// DOCSTART 1
class DummyContractV2 : UpgradedContract<DummyContract.State, DummyContractV2.State> {
    companion object {
        const val PROGRAM_ID: ContractClassName = "net.corda.testing.contracts.DummyContractV2"
    }

    override val legacyContract: String = DummyContract::class.java.name

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
    // DOCEND 1
    /**
     * Generate an upgrade transaction from [DummyContract].
     *
     * Note: This is a convenience helper method used for testing only.
     *
     * @param services Services required to resolve the wire transaction
     * @return a pair of wire transaction, and a set of those who should sign the transaction for it to be valid.
     */
    fun generateUpgradeFromV1(services: ServicesForResolution, vararg states: StateAndRef<DummyContract.State>): Pair<WireTransaction, Set<AbstractParty>> {
        val notary = states.map { it.state.notary }.single()
        require(states.isNotEmpty())

        val signees: Set<AbstractParty> = states.flatMap { it.state.data.participants }.distinct().toSet()
        return Pair(TransactionBuilder(notary).apply {
            states.forEach {
                addInputState(it)
                addOutputState(upgrade(it.state.data), DummyContractV2.PROGRAM_ID, it.state.constraint)
                addCommand(UpgradeCommand(DummyContractV2.PROGRAM_ID), signees.map { it.owningKey }.toList())
            }
        }.toWireTransaction(services), signees)
    }
}

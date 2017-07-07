package net.corda.testing.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.WireTransaction
import net.corda.flows.ContractUpgradeFlow

// The dummy contract doesn't do anything useful. It exists for testing purposes.
val DUMMY_V2_PROGRAM_ID = DummyContractV2()

/**
 * Dummy contract state for testing of the upgrade process.
 */
// DOCSTART 1
class DummyContractV2 : UpgradedContract<DummyContract.State, DummyContractV2.State> {
    override val legacyContract = DummyContract::class.java

    data class State(val magicNumber: Int = 0, val owners: List<AbstractParty>) : ContractState {
        override val contract = DUMMY_V2_PROGRAM_ID
        override val participants: List<AbstractParty> = owners
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    override fun upgrade(state: DummyContract.State): State {
        return State(state.magicNumber, state.participants)
    }

    override fun verify(tx: TransactionForContract) {
        if (tx.commands.any { it.value is UpgradeCommand }) ContractUpgradeFlow.verify(tx)
        // Other verifications.
    }

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("")
    // DOCEND 1
    /**
     * Generate an upgrade transaction from [DummyContract].
     *
     * Note: This is a convenience helper method used for testing only.
     *
     * @return a pair of wire transaction, and a set of those who should sign the transaction for it to be valid.
     */
    fun generateUpgradeFromV1(vararg states: StateAndRef<DummyContract.State>): Pair<WireTransaction, Set<AbstractParty>> {
        val notary = states.map { it.state.notary }.single()
        require(states.isNotEmpty())

        val signees: Set<AbstractParty> = states.flatMap { it.state.data.participants }.distinct().toSet()
        return Pair(TransactionType.General.Builder(notary).apply {
            states.forEach {
                addInputState(it)
                addOutputState(upgrade(it.state.data))
                addCommand(UpgradeCommand(DUMMY_V2_PROGRAM_ID.javaClass), signees.map { it.owningKey }.toList())
            }
        }.toWireTransaction(), signees)
    }
}

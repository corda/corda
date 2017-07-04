package net.corda.core.contracts.testing

// The dummy contract doesn't do anything useful. It exists for testing purposes.
val DUMMY_V2_PROGRAM_ID = net.corda.core.contracts.testing.DummyContractV2()

/**
 * Dummy contract state for testing of the upgrade process.
 */
// DOCSTART 1
class DummyContractV2 : net.corda.core.contracts.UpgradedContract<DummyContract.State, DummyContractV2.State> {
    override val legacyContract = DummyContract::class.java

    data class State(val magicNumber: Int = 0, val owners: List<net.corda.core.identity.AbstractParty>) : net.corda.core.contracts.ContractState {
        override val contract = net.corda.core.contracts.testing.DUMMY_V2_PROGRAM_ID
        override val participants: List<net.corda.core.identity.AbstractParty> = owners
    }

    interface Commands : net.corda.core.contracts.CommandData {
        class Create : net.corda.core.contracts.TypeOnlyCommandData(), net.corda.core.contracts.testing.DummyContractV2.Commands
        class Move : net.corda.core.contracts.TypeOnlyCommandData(), net.corda.core.contracts.testing.DummyContractV2.Commands
    }

    override fun upgrade(state: DummyContract.State): net.corda.core.contracts.testing.DummyContractV2.State {
        return net.corda.core.contracts.testing.DummyContractV2.State(state.magicNumber, state.participants)
    }

    override fun verify(tx: net.corda.core.contracts.TransactionForContract) {
        if (tx.commands.any { it.value is net.corda.core.contracts.UpgradeCommand }) net.corda.flows.ContractUpgradeFlow.Companion.verify(tx)
        // Other verifications.
    }

    // The "empty contract"
    override val legalContractReference: net.corda.core.crypto.SecureHash = net.corda.core.crypto.SecureHash.Companion.sha256("")
    // DOCEND 1
    /**
     * Generate an upgrade transaction from [DummyContract].
     *
     * Note: This is a convenience helper method used for testing only.
     *
     * @return a pair of wire transaction, and a set of those who should sign the transaction for it to be valid.
     */
    fun generateUpgradeFromV1(vararg states: net.corda.core.contracts.StateAndRef<DummyContract.State>): Pair<net.corda.core.transactions.WireTransaction, Set<net.corda.core.identity.AbstractParty>> {
        val notary = states.map { it.state.notary }.single()
        require(states.isNotEmpty())

        val signees: Set<net.corda.core.identity.AbstractParty> = states.flatMap { it.state.data.participants }.distinct().toSet()
        return Pair(net.corda.core.contracts.TransactionType.General.Builder(notary).apply {
            states.forEach {
                addInputState(it)
                addOutputState(upgrade(it.state.data))
                addCommand(net.corda.core.contracts.UpgradeCommand(DUMMY_V2_PROGRAM_ID.javaClass), signees.map { it.owningKey }.toList())
            }
        }.toWireTransaction(), signees)
    }
}

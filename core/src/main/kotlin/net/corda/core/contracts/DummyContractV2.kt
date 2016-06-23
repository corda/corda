package net.corda.core.contracts

import net.corda.core.contracts.clauses.UpgradeClause
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.WireTransaction

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val DUMMY_V2_PROGRAM_ID = DummyContractV2()

/**
 * Dummy contract for testing of the upgrade process.
 */
class DummyContractV2 : UpgradedContract<DummyContract.State> {
    interface Clauses {
        class Upgrade : UpgradeClause<ContractState, Commands, Unit>() {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Upgrade::class.java)
            override val expectedType: Class<*> = State::class.java
        }
    }

    data class State(val magicNumber: Int = 0,
                     val owners: List<CompositeKey>) : ContractState {
        override val contract = DUMMY_V2_PROGRAM_ID
        override val participants: List<CompositeKey> = owners
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
        data class Upgrade(override val oldContract: Contract,
                           override val newContract: UpgradedContract<State>) : UpgradeCommand<State>, Commands
    }

    fun extractCommands(tx: TransactionForContract) = tx.commands.select<Commands>()
    override fun verify(tx: TransactionForContract) = verifyClause(tx, Clauses.Upgrade(), extractCommands(tx))

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("")

    /**
     * Generate an upgrade transaction from [DummyContract].
     *
     * @return a pair of wire transaction, and a set of those who should sign the transaction for it to be valid.
     */
    fun generateUpgradeFromV1(vararg states: StateAndRef<DummyContract.State>): Pair<WireTransaction, Set<CompositeKey>> {
        val notary = states.map { it.state.notary }.single()
        require(states.isNotEmpty())

        val newContract = this
        val signees = states.flatMap { it.state.data.participants }.toSet()
        return Pair(TransactionType.General.Builder(notary).apply {
            states.forEach {
                addInputState(it)
                addOutputState(upgrade(it.state.data))
            }
            addCommand(DummyContract.Commands.Upgrade(DummyContract(), newContract), signees.toList())
        }.toWireTransaction(), signees)
    }

    override fun upgrade(state: DummyContract.State): DummyContractV2.State
            = State(state.magicNumber, state.participants)
}
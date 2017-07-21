package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.transactions.LedgerTransaction
import java.util.concurrent.atomic.AtomicInteger

internal fun matchedClause(counter: AtomicInteger? = null) = object : Clause<ContractState, CommandData, Unit>() {
    override val requiredCommands: Set<Class<out CommandData>> = emptySet()
    override fun verify(tx: LedgerTransaction,
                        inputs: List<ContractState>,
                        outputs: List<ContractState>,
                        commands: List<AuthenticatedObject<CommandData>>, groupingKey: Unit?): Set<CommandData> {
        counter?.incrementAndGet()
        return emptySet()
    }
}

/** A clause that can never be matched */
internal fun unmatchedClause(counter: AtomicInteger? = null) = object : Clause<ContractState, CommandData, Unit>() {
    override val requiredCommands: Set<Class<out CommandData>> = setOf(object : CommandData {}.javaClass)
    override fun verify(tx: LedgerTransaction,
                        inputs: List<ContractState>,
                        outputs: List<ContractState>,
                        commands: List<AuthenticatedObject<CommandData>>, groupingKey: Unit?): Set<CommandData> {
        counter?.incrementAndGet()
        return emptySet()
    }
}

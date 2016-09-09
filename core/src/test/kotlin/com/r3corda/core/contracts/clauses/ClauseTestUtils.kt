package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.AuthenticatedObject
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.TransactionForContract
import java.util.concurrent.atomic.AtomicInteger

internal fun matchedClause(counter: AtomicInteger? = null) = object : Clause<ContractState, CommandData, Unit>() {
    override fun verify(tx: TransactionForContract,
                        inputs: List<ContractState>,
                        outputs: List<ContractState>,
                        commands: List<AuthenticatedObject<CommandData>>, groupingKey: Unit?): Set<CommandData> {
        counter?.incrementAndGet()
        return emptySet()
    }
}

/** A clause that can never be matched */
internal fun unmatchedClause(counter: AtomicInteger? = null) = object : Clause<ContractState, CommandData, Unit>() {
    override val requiredCommands: Set<Class<out CommandData>> = setOf(object: CommandData {}.javaClass)
    override fun verify(tx: TransactionForContract,
                        inputs: List<ContractState>,
                        outputs: List<ContractState>,
                        commands: List<AuthenticatedObject<CommandData>>, groupingKey: Unit?): Set<CommandData> {
        counter?.incrementAndGet()
        return emptySet()
    }
}
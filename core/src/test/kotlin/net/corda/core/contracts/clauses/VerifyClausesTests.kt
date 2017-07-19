package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.contracts.DummyContract
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Tests for the clause verifier.
 */
class VerifyClausesTests {
    /** Very simple check that the function doesn't error when given any clause */
    @Test
    fun minimal() {
        val clause = object : Clause<ContractState, CommandData, Unit>() {
            override fun verify(tx: LedgerTransaction,
                                inputs: List<ContractState>,
                                outputs: List<ContractState>,
                                commands: List<AuthenticatedObject<CommandData>>, groupingKey: Unit?): Set<CommandData> = emptySet()
        }
        val tx = LedgerTransaction(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256(), null, emptyList(), null, TransactionType.General)
        verifyClause(tx, clause, emptyList<AuthenticatedObject<CommandData>>())
    }

    @Test
    fun errorSuperfluousCommands() {
        val clause = object : Clause<ContractState, CommandData, Unit>() {
            override fun verify(tx: LedgerTransaction,
                                inputs: List<ContractState>,
                                outputs: List<ContractState>,
                                commands: List<AuthenticatedObject<CommandData>>, groupingKey: Unit?): Set<CommandData> = emptySet()
        }
        val command = AuthenticatedObject(emptyList(), emptyList(), DummyContract.Commands.Create())
        val tx = LedgerTransaction(emptyList(), emptyList(), listOf(command), emptyList(), SecureHash.randomSHA256(), null, emptyList(), null, TransactionType.General)
        // The clause is matched, but doesn't mark the command as consumed, so this should error
        assertFailsWith<IllegalStateException> { verifyClause(tx, clause, listOf(command)) }
    }
}

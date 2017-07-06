package net.corda.core.contracts.clauses

import net.corda.core.contracts.AuthenticatedObject
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionForContract
import net.corda.testing.contracts.DummyContract
import net.corda.core.crypto.SecureHash
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
            override fun verify(tx: TransactionForContract,
                                inputs: List<ContractState>,
                                outputs: List<ContractState>,
                                commands: List<AuthenticatedObject<CommandData>>, groupingKey: Unit?): Set<CommandData> = emptySet()
        }
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        verifyClause(tx, clause, emptyList<AuthenticatedObject<CommandData>>())
    }

    @Test
    fun errorSuperfluousCommands() {
        val clause = object : Clause<ContractState, CommandData, Unit>() {
            override fun verify(tx: TransactionForContract,
                                inputs: List<ContractState>,
                                outputs: List<ContractState>,
                                commands: List<AuthenticatedObject<CommandData>>, groupingKey: Unit?): Set<CommandData> = emptySet()
        }
        val command = AuthenticatedObject(emptyList(), emptyList(), DummyContract.Commands.Create())
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), listOf(command), SecureHash.randomSHA256())
        // The clause is matched, but doesn't mark the command as consumed, so this should error
        assertFailsWith<IllegalStateException> { verifyClause(tx, clause, listOf(command)) }
    }
}

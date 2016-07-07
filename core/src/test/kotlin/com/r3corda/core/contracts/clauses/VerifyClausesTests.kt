package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Tests for the clause verifier.
 */
class VerifyClausesTests {
    /** Check that if there's no clauses, verification passes. */
    @Test
    fun `passes empty clauses`() {
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        verifyClauses(tx, emptyList<SingleClause>(), emptyList<AuthenticatedObject<CommandData>>())
    }

    /** Very simple check that the function doesn't error when given any clause */
    @Test
    fun minimal() {
        val clause = object : SingleClause {
            override val requiredCommands: Set<Class<out CommandData>>
                get() = emptySet()
            override val ifMatched: MatchBehaviour
                get() = MatchBehaviour.CONTINUE
            override val ifNotMatched: MatchBehaviour
                get() = MatchBehaviour.CONTINUE

            override fun verify(tx: TransactionForContract, commands: Collection<AuthenticatedObject<CommandData>>): Set<CommandData> = emptySet()
        }
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        verifyClauses(tx, listOf(clause), emptyList<AuthenticatedObject<CommandData>>())
    }

    /** Check that when there are no required commands, a clause always matches */
    @Test
    fun emptyAlwaysMatches() {
        val clause = object : SingleClause {
            override val requiredCommands: Set<Class<out CommandData>>
                get() = emptySet()
            override val ifMatched: MatchBehaviour
                get() = MatchBehaviour.CONTINUE
            override val ifNotMatched: MatchBehaviour
                get() = MatchBehaviour.ERROR

            override fun verify(tx: TransactionForContract, commands: Collection<AuthenticatedObject<CommandData>>): Set<CommandData> = emptySet()
        }
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        // This would error if it wasn't matched
        verifyClauses(tx, listOf(clause), emptyList<AuthenticatedObject<CommandData>>())
    }

    @Test
    fun errorSuperfluousCommands() {
        val clause = object : SingleClause {
            override val requiredCommands: Set<Class<out CommandData>>
                get() = emptySet()
            override val ifMatched: MatchBehaviour
                get() = MatchBehaviour.ERROR
            override val ifNotMatched: MatchBehaviour
                get() = MatchBehaviour.CONTINUE

            override fun verify(tx: TransactionForContract, commands: Collection<AuthenticatedObject<CommandData>>): Set<CommandData>
                    = emptySet()
        }
        val command = AuthenticatedObject(emptyList(), emptyList(), DummyContract.Commands.Create())
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), listOf(command), SecureHash.randomSHA256())
        // The clause is matched, but doesn't mark the command as consumed, so this should error
        assertFailsWith<IllegalStateException> { verifyClauses(tx, listOf(clause), listOf(command)) }
    }

    /** Check triggering of error if matched */
    @Test
    fun errorMatched() {
        val clause = object : SingleClause {
            override val requiredCommands: Set<Class<out CommandData>>
                get() = setOf(DummyContract.Commands.Create::class.java)
            override val ifMatched: MatchBehaviour
                get() = MatchBehaviour.ERROR
            override val ifNotMatched: MatchBehaviour
                get() = MatchBehaviour.CONTINUE

            override fun verify(tx: TransactionForContract, commands: Collection<AuthenticatedObject<CommandData>>): Set<CommandData>
                    = commands.select<DummyContract.Commands.Create>().map { it.value }.toSet()
        }
        var tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())

        // This should pass as it doesn't match
        verifyClauses(tx, listOf(clause), emptyList())

        // This matches and should throw an error
        val command = AuthenticatedObject(emptyList(), emptyList(), DummyContract.Commands.Create())
        tx = TransactionForContract(emptyList(), emptyList(), emptyList(), listOf(command), SecureHash.randomSHA256())
        assertFailsWith<IllegalStateException> { verifyClauses(tx, listOf(clause), listOf(command)) }
    }

    /** Check triggering of error if unmatched */
    @Test
    fun errorUnmatched() {
        val clause = object : SingleClause {
            override val requiredCommands: Set<Class<out CommandData>>
                get() = setOf(DummyContract.Commands.Create::class.java)
            override val ifMatched: MatchBehaviour
                get() = MatchBehaviour.CONTINUE
            override val ifNotMatched: MatchBehaviour
                get() = MatchBehaviour.ERROR

            override fun verify(tx: TransactionForContract, commands: Collection<AuthenticatedObject<CommandData>>): Set<CommandData> = emptySet()
        }
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        assertFailsWith<IllegalStateException> { verifyClauses(tx, listOf(clause), emptyList()) }
    }
}
package com.r3corda.core.contracts.clauses

import com.r3corda.core.contracts.AuthenticatedObject
import com.r3corda.core.contracts.CommandData
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.contracts.TransactionForContract
import com.r3corda.core.crypto.SecureHash
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnyCompositionTests {
    @Test
    fun minimal() {
        val counter = AtomicInteger(0)
        val clause = AnyComposition(matchedClause(counter), matchedClause(counter))
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        verifyClause(tx, clause, emptyList<AuthenticatedObject<CommandData>>())

        // Check that we've run the verify() function of two clauses
        assertEquals(2, counter.get())
    }

    @Test
    fun `not all match`() {
        val counter = AtomicInteger(0)
        val clause = AnyComposition(matchedClause(counter), unmatchedClause(counter))
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        verifyClause(tx, clause, emptyList<AuthenticatedObject<CommandData>>())

        // Check that we've run the verify() function of one clause
        assertEquals(1, counter.get())
    }

    @Test
    fun `none match`() {
        val counter = AtomicInteger(0)
        val clause = AnyComposition(unmatchedClause(counter), unmatchedClause(counter))
        val tx = TransactionForContract(emptyList(), emptyList(), emptyList(), emptyList(), SecureHash.randomSHA256())
        verifyClause(tx, clause, emptyList<AuthenticatedObject<CommandData>>())

        // Check that we've run the verify() function of neither clause
        assertEquals(0, counter.get())
    }
}
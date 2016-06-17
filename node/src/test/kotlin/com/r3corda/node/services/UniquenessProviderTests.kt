package com.r3corda.node.services

import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.testing.MEGA_CORP
import com.r3corda.core.testing.generateStateRef
import com.r3corda.node.services.transactions.InMemoryUniquenessProvider
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UniquenessProviderTests {
    val identity = MEGA_CORP
    val txID = SecureHash.randomSHA256()

    @Test fun `should commit a transaction with unused inputs without exception`() {
        val provider = InMemoryUniquenessProvider()
        val inputState = generateStateRef()

        provider.commit(listOf(inputState), txID, identity)
    }

    @Test fun `should report a conflict for a transaction with previously used inputs`() {
        val provider = InMemoryUniquenessProvider()
        val inputState = generateStateRef()

        val inputs = listOf(inputState)
        provider.commit(inputs, txID, identity)

        val ex = assertFailsWith<UniquenessException> { provider.commit(inputs, txID, identity) }

        val consumingTx = ex.error.stateHistory[inputState]!!
        assertEquals(consumingTx.id, txID)
        assertEquals(consumingTx.inputIndex, inputs.indexOf(inputState))
        assertEquals(consumingTx.requestingParty, identity)
    }
}
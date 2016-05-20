package node.services

import core.contracts.TransactionBuilder
import core.node.services.UniquenessException
import core.testing.MEGA_CORP
import core.testing.generateStateRef
import node.services.transactions.InMemoryUniquenessProvider
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UniquenessProviderTests {
    val identity = MEGA_CORP

    @Test fun `should commit a transaction with unused inputs without exception`() {
        val provider = InMemoryUniquenessProvider()
        val inputState = generateStateRef()
        val tx = TransactionBuilder().withItems(inputState).toWireTransaction()
        provider.commit(tx, identity)
    }

    @Test fun `should report a conflict for a transaction with previously used inputs`() {
        val provider = InMemoryUniquenessProvider()
        val inputState = generateStateRef()

        val tx1 = TransactionBuilder().withItems(inputState).toWireTransaction()
        provider.commit(tx1, identity)

        val tx2 = TransactionBuilder().withItems(inputState).toWireTransaction()
        val ex = assertFailsWith<UniquenessException> { provider.commit(tx2, identity) }

        val consumingTx = ex.error.stateHistory[inputState]!!
        assertEquals(consumingTx.id, tx1.id)
        assertEquals(consumingTx.inputIndex, tx1.inputs.indexOf(inputState))
        assertEquals(consumingTx.requestingParty, identity)
    }
}
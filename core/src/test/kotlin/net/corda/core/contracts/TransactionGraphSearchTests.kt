package net.corda.core.contracts

import net.corda.core.crypto.composite
import net.corda.core.crypto.newSecureRandom
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.node.MockTransactionStorage
import org.junit.Test
import java.security.KeyPair
import kotlin.test.assertEquals

class TransactionGraphSearchTests {
    class GraphTransactionStorage(val originTx: SignedTransaction, val inputTx: SignedTransaction) : MockTransactionStorage() {
        init {
            addTransaction(originTx)
            addTransaction(inputTx)
        }
    }

    fun random31BitValue(): Int = Math.abs(newSecureRandom().nextInt())

    /**
     * Build a pair of transactions. The first issues a dummy output state, and has a command applied, the second then
     * references that state.
     *
     * @param command the command to add to the origin transaction.
     * @param signer signer for the two transactions and their commands.
     */
    fun buildTransactions(command: CommandData, signer: KeyPair): GraphTransactionStorage {
        val originTx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            addOutputState(DummyState(random31BitValue()))
            addCommand(command, signer.public.composite)
            signWith(signer)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction(false)
        val inputTx = TransactionType.General.Builder(DUMMY_NOTARY).apply {
            addInputState(originTx.tx.outRef<DummyState>(0))
            signWith(signer)
            signWith(DUMMY_NOTARY_KEY)
        }.toSignedTransaction(false)
        return GraphTransactionStorage(originTx, inputTx)
    }

    @Test
    fun `return empty from empty`() {
        val storage = buildTransactions(DummyContract.Commands.Create(), MEGA_CORP_KEY)
        val search = TransactionGraphSearch(storage, emptyList())
        search.query = TransactionGraphSearch.Query()
        val expected = emptyList<WireTransaction>()
        val actual = search.call()

        assertEquals(expected, actual)
    }

    @Test
    fun `return empty from no match`() {
        val storage = buildTransactions(DummyContract.Commands.Create(), MEGA_CORP_KEY)
        val search = TransactionGraphSearch(storage, listOf(storage.inputTx.tx))
        search.query = TransactionGraphSearch.Query()
        val expected = emptyList<WireTransaction>()
        val actual = search.call()

        assertEquals(expected, actual)
    }

    @Test
    fun `return origin on match`() {
        val storage = buildTransactions(DummyContract.Commands.Create(), MEGA_CORP_KEY)
        val search = TransactionGraphSearch(storage, listOf(storage.inputTx.tx))
        search.query = TransactionGraphSearch.Query(DummyContract.Commands.Create::class.java)
        val expected = listOf(storage.originTx.tx)
        val actual = search.call()

        assertEquals(expected, actual)
    }
}

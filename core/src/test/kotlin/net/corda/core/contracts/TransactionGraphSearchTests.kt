package net.corda.core.contracts

import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.core.crypto.newSecureRandom
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.DUMMY_NOTARY_KEY
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.MEGA_CORP_PUBKEY
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockTransactionStorage
import org.junit.Test
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
    fun buildTransactions(command: CommandData): GraphTransactionStorage {
        val megaCorpServices = MockServices(MEGA_CORP_KEY)
        val notaryServices = MockServices(DUMMY_NOTARY_KEY)

        val originBuilder = TransactionType.General.Builder(DUMMY_NOTARY)
        originBuilder.addOutputState(DummyState(random31BitValue()))
        originBuilder.addCommand(command, MEGA_CORP_PUBKEY)

        val originPtx = megaCorpServices.signInitialTransaction(originBuilder)
        val originTx = notaryServices.addSignature(originPtx)

        val inputBuilder = TransactionType.General.Builder(DUMMY_NOTARY)
        inputBuilder.addInputState(originTx.tx.outRef<DummyState>(0))

        val inputPtx = megaCorpServices.signInitialTransaction(inputBuilder)
        val inputTx = megaCorpServices.addSignature(inputPtx)

        return GraphTransactionStorage(originTx, inputTx)
    }

    @Test
    fun `return empty from empty`() {
        val storage = buildTransactions(DummyContract.Commands.Create())
        val search = TransactionGraphSearch(storage, emptyList())
        search.query = TransactionGraphSearch.Query()
        val expected = emptyList<WireTransaction>()
        val actual = search.call()

        assertEquals(expected, actual)
    }

    @Test
    fun `return empty from no match`() {
        val storage = buildTransactions(DummyContract.Commands.Create())
        val search = TransactionGraphSearch(storage, listOf(storage.inputTx.tx))
        search.query = TransactionGraphSearch.Query()
        val expected = emptyList<WireTransaction>()
        val actual = search.call()

        assertEquals(expected, actual)
    }

    @Test
    fun `return origin on match`() {
        val storage = buildTransactions(DummyContract.Commands.Create())
        val search = TransactionGraphSearch(storage, listOf(storage.inputTx.tx))
        search.query = TransactionGraphSearch.Query(DummyContract.Commands.Create::class.java)
        val expected = listOf(storage.originTx.tx)
        val actual = search.call()

        assertEquals(expected, actual)
    }
}

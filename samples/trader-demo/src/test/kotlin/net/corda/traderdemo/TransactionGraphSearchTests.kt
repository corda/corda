package net.corda.traderdemo

import net.corda.core.contracts.CommandData
import net.corda.core.crypto.newSecureRandom
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.testing.*
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockTransactionStorage
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class TransactionGraphSearchTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

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
        val megaCorpServices = MockServices(setOf("net.corda.testing.contracts"), rigorousMock(), MEGA_CORP.name, MEGA_CORP_KEY)
        val notaryServices = MockServices(setOf("net.corda.testing.contracts"), rigorousMock(), DUMMY_NOTARY.name, DUMMY_NOTARY_KEY)
        val originBuilder = TransactionBuilder(DUMMY_NOTARY)
                .addOutputState(DummyState(random31BitValue()), DummyContract.PROGRAM_ID)
                .addCommand(command, MEGA_CORP_PUBKEY)

        val originPtx = megaCorpServices.signInitialTransaction(originBuilder)
        val originTx = notaryServices.addSignature(originPtx)

        val inputBuilder = TransactionBuilder(DUMMY_NOTARY)
                .addInputState(originTx.tx.outRef<DummyState>(0))
                .addCommand(dummyCommand(MEGA_CORP_PUBKEY))

        val inputPtx = megaCorpServices.signInitialTransaction(inputBuilder)
        val inputTx = megaCorpServices.addSignature(inputPtx)

        return GraphTransactionStorage(originTx, inputTx)
    }

    @Test
    fun `return empty from empty`() {
        val storage = buildTransactions(DummyContract.Commands.Create())
        val search = TransactionGraphSearch(storage, emptyList(), TransactionGraphSearch.Query())
        val expected = emptyList<WireTransaction>()
        val actual = search.call()

        assertEquals(expected, actual)
    }

    @Test
    fun `return empty from no match`() {
        val storage = buildTransactions(DummyContract.Commands.Create())
        val search = TransactionGraphSearch(storage, listOf(storage.inputTx.tx), TransactionGraphSearch.Query())
        val expected = emptyList<WireTransaction>()
        val actual = search.call()

        assertEquals(expected, actual)
    }

    @Test
    fun `return origin on match`() {
        val storage = buildTransactions(DummyContract.Commands.Create())
        val search = TransactionGraphSearch(storage, listOf(storage.inputTx.tx), TransactionGraphSearch.Query(DummyContract.Commands.Create::class.java))
        val expected = listOf(storage.originTx.tx)
        val actual = search.call()

        assertEquals(expected, actual)
    }
}

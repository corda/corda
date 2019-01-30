package net.corda.traderdemo

import net.corda.core.contracts.CommandData
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.IdentityService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.dummyCommand
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.MockTransactionStorage
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class TransactionGraphSearchTests {
    private companion object {
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    }

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
        val megaCorpServices = MockServices(listOf("net.corda.testing.contracts"), megaCorp, rigorousMock<IdentityService>())
        val notaryServices = MockServices(listOf("net.corda.testing.contracts"), dummyNotary, rigorousMock<IdentityService>())
        val originBuilder = TransactionBuilder(dummyNotary.party)
                .addOutputState(DummyState(random31BitValue()), DummyContract.PROGRAM_ID)
                .addCommand(command, megaCorp.publicKey)

        val originPtx = megaCorpServices.signInitialTransaction(originBuilder)
        val originTx = notaryServices.addSignature(originPtx)
        val inputBuilder = TransactionBuilder(dummyNotary.party)
                .addInputState(originTx.tx.outRef<DummyState>(0))
                .addCommand(dummyCommand(megaCorp.publicKey))

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

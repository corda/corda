package net.corda.core.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.sequence
import net.corda.node.internal.StartedNode
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNetwork.MockNode
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// DOCSTART 3
class ResolveTransactionsFlowTest {
    private lateinit var mockNet: InternalMockNetwork
    private lateinit var notaryNode: StartedNode<MockNode>
    private lateinit var megaCorpNode: StartedNode<MockNode>
    private lateinit var miniCorpNode: StartedNode<MockNode>
    private lateinit var megaCorp: Party
    private lateinit var miniCorp: Party
    private lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(cordappPackages = listOf("net.corda.testing.contracts", "net.corda.core.internal"))
        notaryNode = mockNet.defaultNotaryNode
        megaCorpNode = mockNet.createPartyNode(CordaX500Name("MegaCorp", "London", "GB"))
        miniCorpNode = mockNet.createPartyNode(CordaX500Name("MiniCorp", "London", "GB"))
        notary = mockNet.defaultNotaryIdentity
        megaCorp = megaCorpNode.info.singleIdentity()
        miniCorp = miniCorpNode.info.singleIdentity()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }
    // DOCEND 3

    // DOCSTART 1
    @Test
    fun `resolve from two hashes`() {
        val (stx1, stx2) = makeTransactions()
        val p = TestFlow(setOf(stx2.id), megaCorp)
        val future = miniCorpNode.services.startFlow(p)
        mockNet.runNetwork()
        val results = future.resultFuture.getOrThrow()
        assertEquals(listOf(stx1.id, stx2.id), results.map { it.id })
        miniCorpNode.database.transaction {
            assertEquals(stx1, miniCorpNode.services.validatedTransactions.getTransaction(stx1.id))
            assertEquals(stx2, miniCorpNode.services.validatedTransactions.getTransaction(stx2.id))
        }
    }
    // DOCEND 1

    @Test
    fun `dependency with an error`() {
        val stx = makeTransactions(signFirstTX = false).second
        val p = TestFlow(setOf(stx.id), megaCorp)
        val future = miniCorpNode.services.startFlow(p)
        mockNet.runNetwork()
        assertFailsWith(SignedTransaction.SignaturesMissingException::class) { future.resultFuture.getOrThrow() }
    }

    @Test
    fun `resolve from a signed transaction`() {
        val (stx1, stx2) = makeTransactions()
        val p = TestFlow(stx2, megaCorp)
        val future = miniCorpNode.services.startFlow(p)
        mockNet.runNetwork()
        future.resultFuture.getOrThrow()
        miniCorpNode.database.transaction {
            assertEquals(stx1, miniCorpNode.services.validatedTransactions.getTransaction(stx1.id))
            // But stx2 wasn't inserted, just stx1.
            assertNull(miniCorpNode.services.validatedTransactions.getTransaction(stx2.id))
        }
    }

    @Test
    fun `denial of service check`() {
        // Chain lots of txns together.
        val stx2 = makeTransactions().second
        val count = 50
        var cursor = stx2
        repeat(count) {
            val builder = DummyContract.move(cursor.tx.outRef(0), miniCorp)
            val stx = megaCorpNode.services.signInitialTransaction(builder)
            megaCorpNode.database.transaction {
                megaCorpNode.services.recordTransactions(stx)
            }
            cursor = stx
        }
        val p = TestFlow(setOf(cursor.id), megaCorp, 40)
        val future = miniCorpNode.services.startFlow(p)
        mockNet.runNetwork()
        assertFailsWith<ResolveTransactionsFlow.ExcessivelyLargeTransactionGraph> { future.resultFuture.getOrThrow() }
    }

    @Test
    fun `triangle of transactions resolves fine`() {
        val stx1 = makeTransactions().first

        val stx2 = DummyContract.move(stx1.tx.outRef(0), miniCorp).let { builder ->
            val ptx = megaCorpNode.services.signInitialTransaction(builder)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }

        val stx3 = DummyContract.move(listOf(stx1.tx.outRef(0), stx2.tx.outRef(0)), miniCorp).let { builder ->
            val ptx = megaCorpNode.services.signInitialTransaction(builder)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }

        megaCorpNode.database.transaction {
            megaCorpNode.services.recordTransactions(stx2, stx3)
        }

        val p = TestFlow(setOf(stx3.id), megaCorp)
        val future = miniCorpNode.services.startFlow(p)
        mockNet.runNetwork()
        future.resultFuture.getOrThrow()
    }

    @Test
    fun attachment() {
        fun makeJar(): InputStream {
            val bs = ByteArrayOutputStream()
            val jar = JarOutputStream(bs)
            jar.putNextEntry(JarEntry("TEST"))
            jar.write("Some test file".toByteArray())
            jar.closeEntry()
            jar.close()
            return bs.toByteArray().sequence().open()
        }
        // TODO: this operation should not require an explicit transaction
        val id = megaCorpNode.database.transaction {
            megaCorpNode.services.attachments.importAttachment(makeJar())
        }
        val stx2 = makeTransactions(withAttachment = id).second
        val p = TestFlow(stx2, megaCorp)
        val future = miniCorpNode.services.startFlow(p)
        mockNet.runNetwork()
        future.resultFuture.getOrThrow()

        // TODO: this operation should not require an explicit transaction
        miniCorpNode.database.transaction {
            assertNotNull(miniCorpNode.services.attachments.openAttachment(id))
        }
    }

    // DOCSTART 2
    private fun makeTransactions(signFirstTX: Boolean = true, withAttachment: SecureHash? = null): Pair<SignedTransaction, SignedTransaction> {
        // Make a chain of custody of dummy states and insert into node A.
        val dummy1: SignedTransaction = DummyContract.generateInitial(0, notary, megaCorp.ref(1)).let {
            if (withAttachment != null)
                it.addAttachment(withAttachment)
            when (signFirstTX) {
                true -> {
                    val ptx = megaCorpNode.services.signInitialTransaction(it)
                    notaryNode.services.addSignature(ptx, notary.owningKey)
                }
                false -> {
                    notaryNode.services.signInitialTransaction(it, notary.owningKey)
                }
            }
        }
        val dummy2: SignedTransaction = DummyContract.move(dummy1.tx.outRef(0), miniCorp).let {
            val ptx = megaCorpNode.services.signInitialTransaction(it)
            notaryNode.services.addSignature(ptx, notary.owningKey)
        }
        megaCorpNode.database.transaction {
            megaCorpNode.services.recordTransactions(dummy1, dummy2)
        }
        return Pair(dummy1, dummy2)
    }
    // DOCEND 2

    @InitiatingFlow
    private class TestFlow(val otherSide: Party, private val resolveTransactionsFlowFactory: (FlowSession) -> ResolveTransactionsFlow, private val txCountLimit: Int? = null) : FlowLogic<List<SignedTransaction>>() {
        constructor(txHashes: Set<SecureHash>, otherSide: Party, txCountLimit: Int? = null) : this(otherSide, { ResolveTransactionsFlow(txHashes, it) }, txCountLimit = txCountLimit)
        constructor(stx: SignedTransaction, otherSide: Party) : this(otherSide, { ResolveTransactionsFlow(stx, it) })

        @Suspendable
        override fun call(): List<SignedTransaction> {
            val session = initiateFlow(otherSide)
            val resolveTransactionsFlow = resolveTransactionsFlowFactory(session)
            txCountLimit?.let { resolveTransactionsFlow.transactionCountLimit = it }
            return subFlow(resolveTransactionsFlow)
        }
    }

    @InitiatedBy(TestFlow::class)
    private class TestResponseFlow(val otherSideSession: FlowSession) : FlowLogic<Void?>() {
        @Suspendable
        override fun call() = subFlow(TestDataVendingFlow(otherSideSession))
    }
}

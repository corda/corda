package net.corda.core.flows

import net.corda.core.contracts.DummyContract
import net.corda.core.crypto.NullSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.serialization.opaque
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY_KEY
import net.corda.flows.ResolveTransactionsFlow
import net.corda.node.utilities.transaction
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.MINI_CORP
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SignatureException
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResolveTransactionsFlowTest {
    lateinit var mockNet: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var notary: Party

    @Before
    fun setup() {
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes()
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        notary = nodes.notaryNode.info.notaryIdentity
        mockNet.runNetwork()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
    }

    // DOCSTART 1
    @Test
    fun `resolve from two hashes`() {
        val (stx1, stx2) = makeTransactions()
        val p = ResolveTransactionsFlow(setOf(stx2.id), a.info.legalIdentity)
        val future = b.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        val results = future.getOrThrow()
        assertEquals(listOf(stx1.id, stx2.id), results.map { it.id })
        b.database.transaction {
            assertEquals(stx1, b.services.validatedTransactions.getTransaction(stx1.id))
            assertEquals(stx2, b.services.validatedTransactions.getTransaction(stx2.id))
        }
    }
    // DOCEND 1

    @Test
    fun `dependency with an error`() {
        val stx = makeTransactions(signFirstTX = false).second
        val p = ResolveTransactionsFlow(setOf(stx.id), a.info.legalIdentity)
        val future = b.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        assertFailsWith(SignatureException::class) { future.getOrThrow() }
    }

    @Test
    fun `resolve from a signed transaction`() {
        val (stx1, stx2) = makeTransactions()
        val p = ResolveTransactionsFlow(stx2, a.info.legalIdentity)
        val future = b.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        future.getOrThrow()
        b.database.transaction {
            assertEquals(stx1, b.services.validatedTransactions.getTransaction(stx1.id))
            // But stx2 wasn't inserted, just stx1.
            assertNull(b.services.validatedTransactions.getTransaction(stx2.id))
        }
    }

    @Test
    fun `denial of service check`() {
        // Chain lots of txns together.
        val stx2 = makeTransactions().second
        val count = 50
        var cursor = stx2
        repeat(count) {
            val stx = DummyContract.move(cursor.tx.outRef(0), MINI_CORP)
                    .addSignatureUnchecked(NullSignature)
                    .toSignedTransaction(false)
            a.database.transaction {
                a.services.recordTransactions(stx)
            }
            cursor = stx
        }
        val p = ResolveTransactionsFlow(setOf(cursor.id), a.info.legalIdentity)
        p.transactionCountLimit = 40
        val future = b.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        assertFailsWith<ResolveTransactionsFlow.ExcessivelyLargeTransactionGraph> { future.getOrThrow() }
    }

    @Test
    fun `triangle of transactions resolves fine`() {
        val stx1 = makeTransactions().first

        val stx2 = DummyContract.move(stx1.tx.outRef(0), MINI_CORP).run {
            signWith(MEGA_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
            toSignedTransaction()
        }

        val stx3 = DummyContract.move(listOf(stx1.tx.outRef(0), stx2.tx.outRef(0)), MINI_CORP).run {
            signWith(MEGA_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
            toSignedTransaction()
        }

        a.database.transaction {
            a.services.recordTransactions(stx2, stx3)
        }

        val p = ResolveTransactionsFlow(setOf(stx3.id), a.info.legalIdentity)
        val future = b.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        future.getOrThrow()
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
            return bs.toByteArray().opaque().open()
        }
        // TODO: this operation should not require an explicit transaction
        val id = a.database.transaction {
            a.services.attachments.importAttachment(makeJar())
        }
        val stx2 = makeTransactions(withAttachment = id).second
        val p = ResolveTransactionsFlow(stx2, a.info.legalIdentity)
        val future = b.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        future.getOrThrow()

        // TODO: this operation should not require an explicit transaction
        b.database.transaction {
            assertNotNull(b.services.attachments.openAttachment(id))
        }
    }

    // DOCSTART 2
    private fun makeTransactions(signFirstTX: Boolean = true, withAttachment: SecureHash? = null): Pair<SignedTransaction, SignedTransaction> {
        // Make a chain of custody of dummy states and insert into node A.
        val dummy1: SignedTransaction = DummyContract.generateInitial(0, notary, MEGA_CORP.ref(1)).let {
            if (withAttachment != null)
                it.addAttachment(withAttachment)
            if (signFirstTX)
                it.signWith(MEGA_CORP_KEY)
            it.signWith(DUMMY_NOTARY_KEY)
            it.toSignedTransaction(false)
        }
        val dummy2: SignedTransaction = DummyContract.move(dummy1.tx.outRef(0), MINI_CORP).let {
            it.signWith(MEGA_CORP_KEY)
            it.signWith(DUMMY_NOTARY_KEY)
            it.toSignedTransaction()
        }
        a.database.transaction {
            a.services.recordTransactions(dummy1, dummy2)
        }
        return Pair(dummy1, dummy2)
    }
    // DOCEND 2
}

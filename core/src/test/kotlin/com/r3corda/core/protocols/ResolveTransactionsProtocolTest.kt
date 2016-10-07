package com.r3corda.core.protocols

import com.r3corda.core.contracts.DummyContract
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.crypto.NullSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.recordTransactions
import com.r3corda.core.serialization.opaque
import com.r3corda.core.utilities.DUMMY_NOTARY_KEY
import com.r3corda.testing.node.MockNetwork
import com.r3corda.protocols.ResolveTransactionsProtocol
import com.r3corda.testing.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.SignatureException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResolveTransactionsProtocolTest {
    lateinit var net: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var notary: Party

    @Before
    fun setup() {
        net = MockNetwork()
        val nodes = net.createSomeNodes()
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        notary = nodes.notaryNode.info.notaryIdentity
        net.runNetwork()
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }

    @Test
    fun `resolve from two hashes`() {
        val (stx1, stx2) = makeTransactions()
        val p = ResolveTransactionsProtocol(setOf(stx2.id), a.info.legalIdentity)
        val future = b.services.startProtocol(p)
        net.runNetwork()
        val results = future.get()
        assertEquals(listOf(stx1.id, stx2.id), results.map { it.id })
        assertEquals(stx1, b.storage.validatedTransactions.getTransaction(stx1.id))
        assertEquals(stx2, b.storage.validatedTransactions.getTransaction(stx2.id))
    }

    @Test
    fun `dependency with an error`() {
        val stx = makeTransactions(signFirstTX = false).second
        val p = ResolveTransactionsProtocol(setOf(stx.id), a.info.legalIdentity)
        val future = b.services.startProtocol(p)
        net.runNetwork()
        assertFailsWith(SignatureException::class) {
            rootCauseExceptions { future.get() }
        }
    }

    @Test
    fun `resolve from a signed transaction`() {
        val (stx1, stx2) = makeTransactions()
        val p = ResolveTransactionsProtocol(stx2, a.info.legalIdentity)
        val future = b.services.startProtocol(p)
        net.runNetwork()
        future.get()
        assertEquals(stx1, b.storage.validatedTransactions.getTransaction(stx1.id))
        // But stx2 wasn't inserted, just stx1.
        assertNull(b.storage.validatedTransactions.getTransaction(stx2.id))
    }

    @Test
    fun `denial of service check`() {
        // Chain lots of txns together.
        val stx2 = makeTransactions().second
        val count = 50
        var cursor = stx2
        repeat(count) {
            val stx = DummyContract.move(cursor.tx.outRef(0), MINI_CORP_PUBKEY)
                    .addSignatureUnchecked(NullSignature)
                    .toSignedTransaction(false)
            a.services.recordTransactions(stx)
            cursor = stx
        }
        val p = ResolveTransactionsProtocol(setOf(cursor.id), a.info.legalIdentity)
        p.transactionCountLimit = 40
        val future = b.services.startProtocol(p)
        net.runNetwork()
        assertFailsWith<ResolveTransactionsProtocol.ExcessivelyLargeTransactionGraph> {
            rootCauseExceptions { future.get() }
        }
    }

    @Test
    fun `triangle of transactions resolves fine`() {
        val stx1 = makeTransactions().first

        val stx2 = DummyContract.move(stx1.tx.outRef(0), MINI_CORP_PUBKEY).run {
            signWith(MEGA_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
            toSignedTransaction()
        }

        val stx3 = DummyContract.move(listOf(stx1.tx.outRef(0), stx2.tx.outRef(0)), MINI_CORP_PUBKEY).run {
            signWith(MEGA_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
            toSignedTransaction()
        }

        a.services.recordTransactions(stx2, stx3)

        val p = ResolveTransactionsProtocol(setOf(stx3.id), a.info.legalIdentity)
        val future = b.services.startProtocol(p)
        net.runNetwork()
        future.get()
    }

    @Test
    fun attachment() {
        val id = a.services.storageService.attachments.importAttachment("Some test file".toByteArray().opaque().open())
        val stx2 = makeTransactions(withAttachment = id).second
        val p = ResolveTransactionsProtocol(stx2, a.info.legalIdentity)
        val future = b.services.startProtocol(p)
        net.runNetwork()
        future.get()
        assertNotNull(b.services.storageService.attachments.openAttachment(id))
    }

    private fun makeTransactions(signFirstTX: Boolean = true, withAttachment: SecureHash? = null): Pair<SignedTransaction, SignedTransaction> {
        // Make a chain of custody of dummy states and insert into node A.
        val dummy1: SignedTransaction = DummyContract.generateInitial(MEGA_CORP.ref(1), 0, notary).let {
            if (withAttachment != null)
                it.addAttachment(withAttachment)
            if (signFirstTX)
                it.signWith(MEGA_CORP_KEY)
            it.signWith(DUMMY_NOTARY_KEY)
            it.toSignedTransaction(false)
        }
        val dummy2: SignedTransaction = DummyContract.move(dummy1.tx.outRef(0), MINI_CORP_PUBKEY).let {
            it.signWith(MEGA_CORP_KEY)
            it.signWith(DUMMY_NOTARY_KEY)
            it.toSignedTransaction()
        }
        a.services.recordTransactions(dummy1, dummy2)
        return Pair(dummy1, dummy2)
    }
}

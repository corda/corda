/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash.Companion.zeroHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.WireTransaction
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.LedgerDSL
import net.corda.testing.dsl.TestLedgerDSLInterpreter
import net.corda.testing.dsl.TestTransactionDSLInterpreter
import net.corda.testing.internal.TEST_TX_TIME
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.PublicKey
import java.util.function.Predicate
import java.util.stream.IntStream
import kotlin.streams.toList
import kotlin.test.*

class PartialMerkleTreeTest {
    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_PUBKEY get() = megaCorp.publicKey
        val MINI_CORP get() = miniCorp.party
        val MINI_CORP_PUBKEY get() = miniCorp.publicKey
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val nodes = "abcdef"
    private lateinit var hashed: List<SecureHash.SHA256>
    private lateinit var expectedRoot: SecureHash
    private lateinit var merkleTree: MerkleTree
    private lateinit var testLedger: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>
    private lateinit var txs: List<WireTransaction>
    private lateinit var testTx: WireTransaction
    @Before
    fun init() {
        hashed = nodes.map { it.serialize().sha256() }
        expectedRoot = MerkleTree.getMerkleTree(hashed.toMutableList() + listOf(zeroHash, zeroHash)).hash
        merkleTree = MerkleTree.getMerkleTree(hashed)
        testLedger = MockServices(emptyList(), MEGA_CORP.name, rigorousMock<IdentityServiceInternal>().also {
            doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
        }).ledger(DUMMY_NOTARY) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "MEGA_CORP cash",
                        Cash.State(
                                amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MEGA_CORP))
                output(Cash.PROGRAM_ID, "dummy cash 1",
                        Cash.State(
                                amount = 900.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MINI_CORP))
            }
            transaction {
                attachments(Cash.PROGRAM_ID)
                input("MEGA_CORP cash")
                output(Cash.PROGRAM_ID, "MEGA_CORP cash".output<Cash.State>().copy(owner = MINI_CORP))
                command(MEGA_CORP_PUBKEY, Cash.Commands.Move())
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
        }
        txs = testLedger.interpreter.transactionsToVerify
        testTx = txs[0]
    }

    // Building full Merkle Tree tests.
    @Test
    fun `building Merkle tree with 6 nodes - no rightmost nodes`() {
        assertEquals(expectedRoot, merkleTree.hash)
    }

    @Test
    fun `building Merkle tree - no hashes`() {
        assertFailsWith<MerkleTreeException> { MerkleTree.getMerkleTree(emptyList()) }
    }

    @Test
    fun `building Merkle tree one node`() {
        val node = 'a'.serialize().sha256()
        val mt = MerkleTree.getMerkleTree(listOf(node))
        assertEquals(node, mt.hash)
    }

    @Test
    fun `building Merkle tree odd number of nodes`() {
        val odd = hashed.subList(0, 3)
        val h1 = hashed[0].hashConcat(hashed[1])
        val h2 = hashed[2].hashConcat(zeroHash)
        val expected = h1.hashConcat(h2)
        val mt = MerkleTree.getMerkleTree(odd)
        assertEquals(mt.hash, expected)
    }

    @Test
    fun `check full tree`() {
        val h = SecureHash.randomSHA256()
        val left = MerkleTree.Node(h, MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h)),
                MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h)))
        val right = MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h))
        val tree = MerkleTree.Node(h, left, right)
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(tree, listOf(h)) }
        PartialMerkleTree.build(right, listOf(h, h)) // Node and two leaves.
        PartialMerkleTree.build(MerkleTree.Leaf(h), listOf(h)) // Just a leaf.
    }

    @Test
    fun `building Merkle tree for a tx and nonce test`() {
        fun filtering(elem: Any): Boolean {
            return when (elem) {
                is StateRef -> true
                is TransactionState<*> -> elem.data.participants[0].owningKey.keys == MINI_CORP_PUBKEY.keys
                is Command<*> -> MEGA_CORP_PUBKEY in elem.signers
                is TimeWindow -> true
                is PublicKey -> elem == MEGA_CORP_PUBKEY
                else -> false
            }
        }

        val d = testTx.serialize().deserialize()
        assertEquals(testTx.id, d.id)

        val ftx = testTx.buildFilteredTransaction(Predicate(::filtering))

        // We expect 5 and not 4 component groups, because there is at least one command in the ftx and thus,
        // the signers component is also sent (required for visibility purposes).
        assertEquals(5, ftx.filteredComponentGroups.size)
        assertEquals(1, ftx.inputs.size)
        assertEquals(0, ftx.attachments.size)
        assertEquals(1, ftx.outputs.size)
        assertEquals(1, ftx.commands.size)
        assertNull(ftx.notary)
        assertNotNull(ftx.timeWindow)
        ftx.verify()
    }

    @Test
    fun `same transactions with different notaries have different ids`() {
        // We even use the same privacySalt, and thus the only difference between the two transactions is the notary party.
        val privacySalt = PrivacySalt()
        val wtx1 = makeSimpleCashWtx(DUMMY_NOTARY, privacySalt)
        val wtx2 = makeSimpleCashWtx(MEGA_CORP, privacySalt)
        assertEquals(wtx1.privacySalt, wtx2.privacySalt)
        assertNotEquals(wtx1.id, wtx2.id)
    }

    @Test
    fun `nothing filtered`() {
        val ftxNothing = testTx.buildFilteredTransaction(Predicate { false })
        assertTrue(ftxNothing.componentGroups.isEmpty())
        assertTrue(ftxNothing.attachments.isEmpty())
        assertTrue(ftxNothing.commands.isEmpty())
        assertTrue(ftxNothing.inputs.isEmpty())
        assertTrue(ftxNothing.outputs.isEmpty())
        assertNull(ftxNothing.timeWindow)
        assertTrue(ftxNothing.availableComponentGroups.flatten().isEmpty())
        ftxNothing.verify() // We allow empty ftx transactions (eg from a timestamp authority that blindly signs).
    }

    // Partial Merkle Tree building tests.
    @Test
    fun `build Partial Merkle Tree, only left nodes branch`() {
        val inclHashes = listOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        assertTrue(pmt.verify(merkleTree.hash, inclHashes))
    }

    @Test
    fun `build Partial Merkle Tree, include zero leaves`() {
        val pmt = PartialMerkleTree.build(merkleTree, emptyList())
        assertTrue(pmt.verify(merkleTree.hash, emptyList()))
    }

    @Test
    fun `build Partial Merkle Tree, include all leaves`() {
        val pmt = PartialMerkleTree.build(merkleTree, hashed)
        assertTrue(pmt.verify(merkleTree.hash, hashed))
    }

    @Test
    fun `build Partial Merkle Tree - duplicate leaves failure`() {
        val inclHashes = arrayListOf(hashed[3], hashed[5], hashed[3], hashed[5])
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(merkleTree, inclHashes) }
    }

    @Test
    fun `build Partial Merkle Tree - only duplicate leaves, less included failure`() {
        val leaves = "aaa"
        val hashes = leaves.map { it.serialize().hash }
        val mt = MerkleTree.getMerkleTree(hashes)
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(mt, hashes.subList(0, 1)) }
    }

    @Test
    fun `verify Partial Merkle Tree - too many leaves failure`() {
        val inclHashes = arrayListOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        inclHashes.add(hashed[0])
        assertFalse(pmt.verify(merkleTree.hash, inclHashes))
    }

    @Test
    fun `verify Partial Merkle Tree - too little leaves failure`() {
        val inclHashes = arrayListOf(hashed[3], hashed[5], hashed[0])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        inclHashes.remove(hashed[0])
        assertFalse(pmt.verify(merkleTree.hash, inclHashes))
    }

    @Test
    fun `verify Partial Merkle Tree - duplicate leaves failure`() {
        val mt = MerkleTree.getMerkleTree(hashed.subList(0, 5)) // Odd number of leaves. Last one is duplicated.
        val inclHashes = arrayListOf(hashed[3], hashed[4])
        val pmt = PartialMerkleTree.build(mt, inclHashes)
        inclHashes.add(hashed[4])
        assertFalse(pmt.verify(mt.hash, inclHashes))
    }

    @Test
    fun `verify Partial Merkle Tree - different leaves failure`() {
        val inclHashes = arrayListOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        assertFalse(pmt.verify(merkleTree.hash, listOf(hashed[2], hashed[4])))
    }

    @Test
    fun `verify Partial Merkle Tree - wrong root`() {
        val inclHashes = listOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        val wrongRoot = hashed[3].hashConcat(hashed[5])
        assertFalse(pmt.verify(wrongRoot, inclHashes))
    }

    @Test(expected = Exception::class)
    fun `hash map serialization not allowed`() {
        val hm1 = hashMapOf("a" to 1, "b" to 2, "c" to 3, "e" to 4)
        hm1.serialize()
    }

    private fun makeSimpleCashWtx(
            notary: Party,
            privacySalt: PrivacySalt = PrivacySalt(),
            timeWindow: TimeWindow? = null,
            attachments: List<SecureHash> = emptyList()
    ): WireTransaction {
        return WireTransaction(
                inputs = testTx.inputs,
                attachments = attachments,
                outputs = testTx.outputs,
                commands = testTx.commands,
                notary = notary,
                timeWindow = timeWindow,
                privacySalt = privacySalt
        )
    }

    @Test
    fun `Find leaf index`() {
        // A Merkle tree with 20 leaves.
        val sampleLeaves = IntStream.rangeClosed(0, 19).toList().map { SecureHash.sha256(it.toString()) }
        val merkleTree = MerkleTree.getMerkleTree(sampleLeaves)

        // Provided hashes are not in the tree.
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(merkleTree, listOf<SecureHash>(SecureHash.sha256("20"))) }
        // One of the provided hashes is not in the tree.
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(merkleTree, listOf<SecureHash>(SecureHash.sha256("20"), SecureHash.sha256("1"), SecureHash.sha256("5"))) }

        val pmt = PartialMerkleTree.build(merkleTree, listOf<SecureHash>(SecureHash.sha256("1"), SecureHash.sha256("5"), SecureHash.sha256("0"), SecureHash.sha256("19")))
        // First leaf.
        assertEquals(0, pmt.leafIndex(SecureHash.sha256("0")))
        // Second leaf.
        assertEquals(1, pmt.leafIndex(SecureHash.sha256("1")))
        // A random leaf.
        assertEquals(5, pmt.leafIndex(SecureHash.sha256("5")))
        // The last leaf.
        assertEquals(19, pmt.leafIndex(SecureHash.sha256("19")))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> { pmt.leafIndex(SecureHash.sha256("10")) }
        // The provided hash is not in the tree (using a leaf that didn't exist in the original Merkle tree).
        assertFailsWith<MerkleTreeException> { pmt.leafIndex(SecureHash.sha256("30")) }

        val pmtFirstElementOnly = PartialMerkleTree.build(merkleTree, listOf<SecureHash>(SecureHash.sha256("0")))
        assertEquals(0, pmtFirstElementOnly.leafIndex(SecureHash.sha256("0")))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> { pmtFirstElementOnly.leafIndex(SecureHash.sha256("10")) }

        val pmtLastElementOnly = PartialMerkleTree.build(merkleTree, listOf<SecureHash>(SecureHash.sha256("19")))
        assertEquals(19, pmtLastElementOnly.leafIndex(SecureHash.sha256("19")))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> { pmtLastElementOnly.leafIndex(SecureHash.sha256("10")) }

        val pmtOneElement = PartialMerkleTree.build(merkleTree, listOf<SecureHash>(SecureHash.sha256("5")))
        assertEquals(5, pmtOneElement.leafIndex(SecureHash.sha256("5")))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> { pmtOneElement.leafIndex(SecureHash.sha256("10")) }

        val pmtAllIncluded = PartialMerkleTree.build(merkleTree, sampleLeaves)
        for (i in 0..19) assertEquals(i, pmtAllIncluded.leafIndex(SecureHash.sha256(i.toString())))
        // The provided hash is not in the tree (using a leaf that didn't exist in the original Merkle tree).
        assertFailsWith<MerkleTreeException> { pmtAllIncluded.leafIndex(SecureHash.sha256("30")) }
    }
}

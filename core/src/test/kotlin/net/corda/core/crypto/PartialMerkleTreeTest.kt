package net.corda.core.crypto


import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash.Companion.zeroHash
import net.corda.core.identity.Party
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.WireTransaction
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.testing.*
import org.junit.Test
import java.security.PublicKey
import java.util.function.Predicate
import kotlin.test.*

class PartialMerkleTreeTest : TestDependencyInjectionBase() {
    val nodes = "abcdef"
    val hashed = nodes.map {
        initialiseTestSerialization()
        try {
            it.serialize().sha256()
        } finally {
            resetTestSerialization()
        }
    }
    val expectedRoot = MerkleTree.getMerkleTree(hashed.toMutableList() + listOf(zeroHash, zeroHash)).hash
    val merkleTree = MerkleTree.getMerkleTree(hashed)

    val testLedger = ledger {
        unverifiedTransaction {
            output("MEGA_CORP cash") {
                Cash.State(
                        amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                        owner = MEGA_CORP
                )
            }
            output("dummy cash 1") {
                Cash.State(
                        amount = 900.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                        owner = MINI_CORP
                )
            }
        }

        transaction {
            input("MEGA_CORP cash")
            output("MEGA_CORP cash".output<Cash.State>().copy(owner = MINI_CORP))
            command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
            timeWindow(TEST_TX_TIME)
            this.verifies()
        }
    }

    val txs = testLedger.interpreter.transactionsToVerify
    val testTx = txs[0]

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

        val mt = testTx.buildFilteredTransaction(Predicate(::filtering))
        val leaves = mt.filteredLeaves

        assertEquals(1, leaves.inputs.size)
        assertEquals(0, leaves.attachments.size)
        assertEquals(1, leaves.outputs.size)
        assertEquals(1, leaves.commands.size)
        assertNull(mt.filteredLeaves.notary)
        assertNotNull(mt.filteredLeaves.timeWindow)
        assertNull(mt.filteredLeaves.privacySalt)
        assertEquals(4, leaves.nonces.size)
        assertTrue(mt.verify())
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
        val mt = testTx.buildFilteredTransaction(Predicate { false })
        assertTrue(mt.filteredLeaves.attachments.isEmpty())
        assertTrue(mt.filteredLeaves.commands.isEmpty())
        assertTrue(mt.filteredLeaves.inputs.isEmpty())
        assertTrue(mt.filteredLeaves.outputs.isEmpty())
        assertTrue(mt.filteredLeaves.timeWindow == null)
        assertTrue(mt.filteredLeaves.availableComponents.isEmpty())
        assertTrue(mt.filteredLeaves.availableComponentHashes.isEmpty())
        assertTrue(mt.filteredLeaves.nonces.isEmpty())
        assertFailsWith<MerkleTreeException> { mt.verify() }

        // Including only privacySalt still results to an empty FilteredTransaction.
        fun filterPrivacySalt(elem: Any): Boolean = elem is PrivacySalt
        val mt2 = testTx.buildFilteredTransaction(Predicate(::filterPrivacySalt))
        assertTrue(mt2.filteredLeaves.privacySalt == null)
        assertTrue(mt2.filteredLeaves.availableComponents.isEmpty())
        assertTrue(mt2.filteredLeaves.availableComponentHashes.isEmpty())
        assertTrue(mt2.filteredLeaves.nonces.isEmpty())
        assertFailsWith<MerkleTreeException> { mt2.verify() }
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
}

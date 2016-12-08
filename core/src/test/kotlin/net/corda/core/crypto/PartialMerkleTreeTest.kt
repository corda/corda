package net.corda.core.crypto


import com.esotericsoftware.kryo.serializers.MapSerializer
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.`issued by`
import net.corda.core.serialization.*
import net.corda.core.transactions.*
import net.corda.core.utilities.DUMMY_PUBKEY_1
import net.corda.testing.ALICE_PUBKEY
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_PUBKEY
import net.corda.testing.ledger
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartialMerkleTreeTest {
    val nodes = "abcdef"
    val hashed = nodes.map { it.serialize().sha256() }
    val root = SecureHash.parse("F6D8FB3720114F8D040D64F633B0D9178EB09A55AA7D62FAE1A070D1BF561051")
    val merkleTree = MerkleTree.getMerkleTree(hashed)

    val testLedger = ledger {
        unverifiedTransaction {
            output("MEGA_CORP cash") {
                Cash.State(
                        amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                        owner = MEGA_CORP_PUBKEY
                )
            }
        }

        transaction {
            input("MEGA_CORP cash")
            output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
            command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
            this.verifies()
        }
    }

    val testTx = testLedger.interpreter.transactionsToVerify[0]

    //Building full Merkle Tree tests.
    @Test
    fun `building Merkle tree with 6 nodes - no rightmost nodes`() {
        assertEquals(root, merkleTree.hash)
    }

    @Test
    fun `building Merkle tree - no hashes`() {
        assertFailsWith<MerkleTreeException> { MerkleTree.Companion.getMerkleTree(emptyList()) }
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
        val h2 = hashed[2].hashConcat(hashed[2])
        val expected = h1.hashConcat(h2)
        val mt = MerkleTree.getMerkleTree(odd)
        assertEquals(mt.hash, expected)
    }

    @Test
    fun `building Merkle tree for a transaction`() {
        val filterFuns = FilterFuns(
                filterCommands = { x -> ALICE_PUBKEY in x.signers },
                filterOutputs = { true },
                filterInputs = { true })
        val mt = testTx.buildFilteredTransaction(filterFuns)
        val d = WireTransaction.deserialize(testTx.serialized)
        assertEquals(testTx.id, d.id)
        assert(mt.verify(testTx.id))
    }

    //Partial Merkle Tree building tests
    @Test
    fun `build Partial Merkle Tree, only left nodes branch`() {
        val inclHashes = listOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        assert(pmt.verify(merkleTree.hash, inclHashes))
    }

    @Test
    fun `build Partial Merkle Tree, include zero leaves`() {
        val pmt = PartialMerkleTree.build(merkleTree, emptyList())
        assert(pmt.verify(merkleTree.hash, emptyList()))
    }

    @Test
    fun `build Partial Merkle Tree, include all leaves`() {
        val pmt = PartialMerkleTree.build(merkleTree, hashed)
        assert(pmt.verify(merkleTree.hash, hashed))
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
        val mt = MerkleTree.getMerkleTree(hashed.subList(0, 5)) //Odd number of leaves. Last one is duplicated.
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

    @Test
    fun `hash map serialization`() {
        val hm1 = hashMapOf("a" to 1, "b" to 2, "c" to 3, "e" to 4)
        assert(serializedHash(hm1) == serializedHash(hm1.serialize().deserialize())) //It internally uses the ordered HashMap extension.
        val kryo = extendKryoHash(createKryo())
        assertTrue(kryo.getSerializer(HashMap::class.java) is OrderedSerializer)
        assertTrue(kryo.getSerializer(LinkedHashMap::class.java) is MapSerializer)
        val hm2 = hm1.serialize(kryo).deserialize(kryo)
        assert(hm1.hashCode() == hm2.hashCode())
    }
}

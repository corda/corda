package com.r3corda.core.crypto

import com.r3corda.contracts.asset.*
import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.contracts.`with notary`
import com.r3corda.core.serialization.serialize
import com.r3corda.core.transactions.MerkleTransaction
import com.r3corda.core.transactions.hashConcat
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.testing.ALICE_PUBKEY

import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

//todo
//different filtering fun
//verification - different root
//tests failsWith

class PartialMerkleTreeTest{
    val nodes = "abcdef"
    val hashed: MutableList<SecureHash> = ArrayList()
    val root = SecureHash.Companion.parse("F6D8FB3720114F8D040D64F633B0D9178EB09A55AA7D62FAE1A070D1BF561051")
    var firstTwo:SecureHash
    init {
        nodes.mapTo(hashed, { it.serialize().sha256() })
        firstTwo = hashed[0].hashConcat(hashed[1])
    }
    private fun makeTX() = TransactionType.General.Builder(DUMMY_NOTARY).withItems(1000.DOLLARS.CASH `issued by` DUMMY_CASH_ISSUER `owned by` ALICE_PUBKEY `with notary` DUMMY_NOTARY)

    @Test
    fun `building not full Merkle tree with 6 nodes`(){
        assertEquals(6, hashed.size)
        val mtl = ArrayList<SecureHash>()
        mtl.addAll(hashed)
        MerkleTransaction.Companion.buildMerkleTree(mtl, hashed)
        assertEquals(12, mtl.size)
        assertEquals(root, mtl.last())
    }

    @Test
    fun `building Merkle tree one node`(){
        val node = 'a'.serialize().sha256()
        val mtl = mutableListOf<SecureHash>(node)
        MerkleTransaction.Companion.buildMerkleTree(mtl, listOf(node))
        assertEquals(1, mtl.size)
        assertEquals(node, mtl.last())
    }

    @Test
    fun `building Merkle tree odd number of nodes`(){
        val odd = hashed.subList(0, 3)
        val mtl = ArrayList<SecureHash>()
        val h1 = hashed[0].hashConcat(hashed[1])
        val h2 = hashed[2].hashConcat(hashed[2])
        mtl.addAll(odd)
        MerkleTransaction.Companion.buildMerkleTree(mtl, odd)
        assertEquals(6, mtl.size)
        assertEquals(h1, mtl[3])
        assertEquals(h2, mtl[4])
    }

    @Test
    fun `building Merkle tree for a transaction`(){
        val tx = makeTX()
        tx.addCommand(Cash.Commands.Move(), ALICE_PUBKEY)
        tx.addCommand(Cash.Commands.Issue(), ALICE_PUBKEY)
        tx.addCommand(Cash.Commands.Issue(0), ALICE_PUBKEY)
        tx.addCommand(Cash.Commands.Issue(1), ALICE_PUBKEY)
        val wtx = tx.toWireTransaction()
        val mt = MerkleTransaction.buildMerkleTransaction(wtx, {x -> true} )
        assert(mt.verify())
    }

    @Test
    fun `ordering insensitive tree`(){
        val tx1 = makeTX()
        tx1.addCommand(Cash.Commands.Issue(1), ALICE_PUBKEY)
        tx1.addCommand(Cash.Commands.Issue(0), ALICE_PUBKEY)
        val wtx1 = tx1.toWireTransaction()
        val mt1 = MerkleTransaction.buildMerkleTransaction(wtx1, {x -> true} )

        val tx2 = makeTX()
        tx2.addCommand(Cash.Commands.Issue(0), ALICE_PUBKEY)
        tx2.addCommand(Cash.Commands.Issue(1), ALICE_PUBKEY)
        val wtx2 = tx2.toWireTransaction()
        val mt2 = MerkleTransaction.buildMerkleTransaction(wtx2, {x -> true} )
        assert(mt1.merkleRoot == mt2.merkleRoot)
    }

    //Partial Merkle Tree building tests
    @Test
    fun `Partial Merkle Tree, only left nodes branch`(){
        val includeLeaves = listOf(false, false, false, true, false, true)
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assert(!pmt.includeBranch[2] && !pmt.includeBranch[4]&& !pmt.includeBranch[8])
        assertEquals(5, pmt.branchHashes.size)
        assertEquals(10, pmt.includeBranch.size)
    }

    @Test
    fun `Partial Merkle Tree, include zero leaves`(){
        val includeLeaves = listOf(false, false, false, false, false, false)
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assertEquals(1, pmt.branchHashes.size)
        assertEquals(1, pmt.includeBranch.size)
        assertEquals(root, pmt.branchHashes[0])
    }

    @Test
    fun `Partial Merkle Tree, include all leaves`(){
        val includeLeaves = listOf(true, true, true, true, true, true)
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assertEquals(6, pmt.branchHashes.size)
        assertEquals(12, pmt.includeBranch.size)
        assertEquals(pmt.branchHashes, hashed)
    }

    //Verification
    @Test
    fun `verify Partial Merkle Tree on a simple example`(){
        val includeLeaves = listOf(false, false, false, true, false, true)
        val inclHashes = listOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assert(pmt.verify(inclHashes, root))
    }

    @Test
    fun `verify Partial Merkle Tree - failure`(){
        val includeLeaves = listOf(false, false, false, true, false, true)
        val inclHashes = listOf(hashed[3], hashed[5], hashed[0])
        val pmt = PartialMerkleTree.build(includeLeaves, hashed)
        assertFalse(pmt.verify(inclHashes, root))
    }
}

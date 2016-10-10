package com.r3corda.core.crypto

import com.r3corda.core.serialization.serialize
import com.r3corda.core.transactions.MerkleTransaction
import com.r3corda.core.transactions.hashConcat

import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

//todo
//transaction tests
//tests failsWith
//verification tests
//ordering insensitive tests
//filter commands tests

class PartialMerkleTreeTest{
    val nodes = "abcdef"
    val hashed: MutableList<SecureHash> = ArrayList()
    val includeBranch: MutableList<Boolean> = ArrayList()
    val branchHashes: MutableList<SecureHash> = ArrayList()
    val root = SecureHash.Companion.parse("F6D8FB3720114F8D040D64F633B0D9178EB09A55AA7D62FAE1A070D1BF561051")
    var firstTwo:SecureHash
    init {
        nodes.mapTo(hashed, { it.serialize().sha256() })
        firstTwo = hashed[0].hashConcat(hashed[1])
    }

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
        val root = SecureHash.Companion.parse("08CB04523D895A51CDB818655668E7D6809A1FB8A8861845125FAAA86608F607")
        assertEquals(root, mtl.last())
    }

    //todo with tx
//    //create wire transaction
//    @Test
//    fun `building Merkle tree for a transaction`(){
//
//    }
//
    //Partial Merkle Tree tests
    @Test
    fun `Partial Merkle Tree, only left nodes branch`(){
        val includeLeaves = listOf(false, false, false, true, false, true)
        PartialMerkleTree.Companion.whichNodesInBranch(3, 0, includeLeaves, hashed, includeBranch, branchHashes)
        assert(!includeBranch[2] && !includeBranch[4]&& !includeBranch[8])
        assertEquals(5, branchHashes.size)
        assertEquals(10, includeBranch.size)
    }

    @Test
    fun `Partial Merkle Tree, include zero leaves`(){
        val includeLeaves = listOf(false, false, false, false, false, false)
        PartialMerkleTree.Companion.whichNodesInBranch(3, 0, includeLeaves, hashed, includeBranch, branchHashes)
        assertEquals(1, branchHashes.size)
        assertEquals(1, includeBranch.size)
        assertEquals(root, branchHashes[0])
    }

    @Test
    fun `Partial Merkle Tree, include all leaves`(){
        val includeLeaves = listOf(true, true, true, true, true, true)
        PartialMerkleTree.Companion.whichNodesInBranch(3, 0, includeLeaves, hashed, includeBranch, branchHashes)
        assertEquals(6, branchHashes.size)
        assertEquals(12, includeBranch.size)
        assertEquals(branchHashes, hashed)
    }

    @Test
    fun `tree hash root`(){
        val s = PartialMerkleTree.Companion.treeHash(0, 3, hashed)
        assertEquals(root, s)
    }

    @Test
    fun `tree hash first two`(){
        val s = PartialMerkleTree.Companion.treeHash(0, 1, hashed)
        assertEquals(firstTwo, s)
    }
}

package net.corda.deterministic.crypto

import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import org.junit.Assert.assertEquals
import org.junit.Test

class MerkleTreeTest {
    @Test
    fun testCreate() {
        val merkle = MerkleTree.getMerkleTree(listOf(SecureHash.allOnesHash, SecureHash.zeroHash))
        assertEquals(SecureHash.parse("A5DE9B714ACCD8AFAAABF1CBD6E1014C9D07FF95C2AE154D91EC68485B31E7B5"), merkle.hash)
    }
}
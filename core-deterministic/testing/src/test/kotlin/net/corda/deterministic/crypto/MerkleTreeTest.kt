package net.corda.deterministic.crypto

import net.corda.core.crypto.SHA2256DigestService
import net.corda.core.crypto.SHA3256DigestService
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import org.junit.Assert.assertEquals
import org.junit.Test

class MerkleTreeTest {
    @Test(timeout=300_000)
	fun testCreate() {
        val merkle = MerkleTree.getMerkleTree(listOf(SecureHash.allOnesHash, SecureHash.zeroHash), SHA2256DigestService())
        assertEquals(SecureHash.create("A5DE9B714ACCD8AFAAABF1CBD6E1014C9D07FF95C2AE154D91EC68485B31E7B5"), merkle.hash)
    }

    @Test(timeout=300_000)
    fun testCreateSHA3256() {
        val merkle = MerkleTree.getMerkleTree(listOf(SecureHash.allOnesHashFor(SecureHash.SHA3_256),
                SecureHash.zeroHashFor(SecureHash.SHA3_256)), SHA3256DigestService())
        assertEquals(SecureHash.create("SHA3-256:80673DBEEC8F6761ACBB121E7E45F61D4279CCD8B8E2231741ECD0716F4C9EDC"), merkle.hash)
    }

    @Test(timeout=300_000)
    fun testCreateSHA2256toSHA3256() {
        val merkle = MerkleTree.getMerkleTree(listOf(SecureHash.allOnesHash, SecureHash.zeroHash), SHA3256DigestService())
        assertEquals(SecureHash.create("SHA3-256:80673DBEEC8F6761ACBB121E7E45F61D4279CCD8B8E2231741ECD0716F4C9EDC"), merkle.hash)
    }

    @Test(timeout=300_000)
    fun testCreateSHA3256toSHA2256() {
        val merkle = MerkleTree.getMerkleTree(listOf(SecureHash.allOnesHashFor(SecureHash.SHA3_256),
                SecureHash.zeroHashFor(SecureHash.SHA3_256)), SHA2256DigestService())
        assertEquals(SecureHash.create("A5DE9B714ACCD8AFAAABF1CBD6E1014C9D07FF95C2AE154D91EC68485B31E7B5"), merkle.hash)
    }
}
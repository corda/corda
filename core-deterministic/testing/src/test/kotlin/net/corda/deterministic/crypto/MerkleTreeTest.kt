package net.corda.deterministic.crypto

import net.corda.core.crypto.DigestService
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.SecureHash
import org.junit.Assert.assertEquals
import org.junit.Test

class MerkleTreeTest {
    private fun leafs(algorithm : String) : List<SecureHash> =
            listOf(SecureHash.allOnesHashFor(algorithm), SecureHash.zeroHashFor(algorithm))

    @Test(timeout=300_000)
	fun testCreate() {
        val merkle = MerkleTree.getMerkleTree(leafs(SecureHash.SHA2_256), DigestService.sha2_256)
        assertEquals(SecureHash.create("A5DE9B714ACCD8AFAAABF1CBD6E1014C9D07FF95C2AE154D91EC68485B31E7B5"), merkle.hash)
    }

    @Test(timeout=300_000)
    fun `test create SHA2-384`() {
        val merkle = MerkleTree.getMerkleTree(leafs(SecureHash.SHA2_384), DigestService.sha2_384)
        assertEquals(SecureHash.create("SHA-384:2B83D37859E3665D7C239964D769CF950EE6478C13E4CA2D6643C23B6C4EAE035C88F654D22E0D65E7CA40BAE4F3718F"), merkle.hash)
    }

    @Test(timeout=300_000)
    fun `test create SHA2-256 to SHA2-384`() {
        val merkle = MerkleTree.getMerkleTree(leafs(SecureHash.SHA2_256), DigestService.sha2_384)
        assertEquals(SecureHash.create("SHA-384:02A4E8EA5AA4BBAFE80C0E7127B15994B84030BE8616EA2A0127D85203CF34221403635C08084A6BDDB1DB06333F0A49"), merkle.hash)
    }

//    @Test(timeout=300_000)
//    fun testCreateSHA3256() {
//        val merkle = MerkleTree.getMerkleTree(listOf(SecureHash.allOnesHashFor(SecureHash.SHA3_256),
//                SecureHash.zeroHashFor(SecureHash.SHA3_256)), DigestService.sha3_256)
//        assertEquals(SecureHash.create("SHA3-256:80673DBEEC8F6761ACBB121E7E45F61D4279CCD8B8E2231741ECD0716F4C9EDC"), merkle.hash)
//    }
//
//    @Test(timeout=300_000)
//    fun testCreateSHA2256toSHA3256() {
//        val merkle = MerkleTree.getMerkleTree(listOf(SecureHash.allOnesHash, SecureHash.zeroHash), DigestService.sha3_256)
//        assertEquals(SecureHash.create("SHA3-256:80673DBEEC8F6761ACBB121E7E45F61D4279CCD8B8E2231741ECD0716F4C9EDC"), merkle.hash)
//    }
//
//    @Test(timeout=300_000)
//    fun testCreateSHA3256toSHA2256() {
//        val merkle = MerkleTree.getMerkleTree(listOf(SecureHash.allOnesHashFor(SecureHash.SHA3_256),
//                SecureHash.zeroHashFor(SecureHash.SHA3_256)), DigestService.sha2_256)
//        assertEquals(SecureHash.create("A5DE9B714ACCD8AFAAABF1CBD6E1014C9D07FF95C2AE154D91EC68485B31E7B5"), merkle.hash)
//    }
}
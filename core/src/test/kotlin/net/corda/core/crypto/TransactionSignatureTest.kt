package net.corda.core.crypto

import net.corda.core.serialization.serialize
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import java.math.BigInteger
import java.security.KeyPair
import java.security.SignatureException
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Transaction signature tests.
 */
class TransactionSignatureTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val testBytes = "12345678901234567890123456789012".toByteArray()

    /** Valid sign and verify. */
    @Test
    fun `Signature metadata full sign and verify`() {
        val txID = testBytes.sha256()
        val keyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)

        // Create a SignableData object.
        val signableData = SignableData(txID, SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))

        // Sign the meta object.
        val transactionSignature: TransactionSignature = keyPair.sign(signableData)

        // Check auto-verification.
        assertTrue(transactionSignature.verify(txID))

        // Check manual verification.
        assertTrue(Crypto.doVerify(txID, transactionSignature))
    }

    /** Verification should fail; corrupted metadata - clearData (Merkle root) has changed. */
    @Test(expected = SignatureException::class)
    fun `Signature metadata full failure clearData has changed`() {
        val keyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))
        val transactionSignature = keyPair.sign(signableData)
        Crypto.doVerify((testBytes + testBytes).sha256(), transactionSignature)
    }

    @Test
    fun `Verify multi-tx signature`() {
        val keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(1234567890L))
        // Deterministically create 5 txIds.
        val txIds: List<SecureHash> = IntRange(0, 4).map { byteArrayOf(it.toByte()).sha256() }
        // Multi-tx signature.
        val txSignature = signMultipleTx(txIds, keyPair)

        // The hash of all txIds are used as leaves.
        val merkleTree = MerkleTree.getMerkleTree(txIds.map { it.sha256() })

        // We haven't added the partial tree yet.
        assertNull(txSignature.partialMerkleTree)
        // Because partial tree is still null, but we signed over a block of txs, verifying a single tx will fail.
        assertFailsWith<SignatureException> { Crypto.doVerify(txIds[3], txSignature) }

        // Create a partial tree for one tx.
        val pmt = PartialMerkleTree.build(merkleTree, listOf(txIds[0].sha256()))
        // Add the partial Merkle tree to the tx signature.
        val txSignatureWithTree = TransactionSignature(txSignature.bytes, txSignature.by, txSignature.signatureMetadata, pmt)

        // Verify the corresponding txId with every possible way.
        assertTrue(Crypto.doVerify(txIds[0], txSignatureWithTree))
        assertTrue(txSignatureWithTree.verify(txIds[0]))
        assertTrue(Crypto.isValid(txIds[0], txSignatureWithTree))
        assertTrue(txSignatureWithTree.isValid(txIds[0]))

        // Verify the rest txs in the block, which are not included in the partial Merkle tree.
        txIds.subList(1, txIds.size).forEach {
            assertFailsWith<IllegalArgumentException> { Crypto.doVerify(it, txSignatureWithTree) }
        }

        // Test that the Merkle tree consists of hash(txId), not txId.
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(merkleTree, listOf(txIds[0])) }

        // What if we send the Full tree. This could be used if notaries didn't want to create a per tx partial tree.
        // Create a partial tree for all txs, thus all leaves are included.
        val pmtFull = PartialMerkleTree.build(merkleTree, txIds.map { it.sha256() })
        // Add the partial Merkle tree to the tx.
        val txSignatureWithFullTree = TransactionSignature(txSignature.bytes, txSignature.by, txSignature.signatureMetadata, pmtFull)

        // All txs can be verified, as they are all included in the provided partial tree.
        txIds.forEach {
            assertTrue(Crypto.doVerify(it, txSignatureWithFullTree))
        }
    }

    @Test
    fun `Verify one-tx signature`() {
        val keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(1234567890L))
        val txId = "aTransaction".toByteArray().sha256()
        // One-tx signature.
        val txSignature = signOneTx(txId, keyPair)

        // partialMerkleTree should be null.
        assertNull(txSignature.partialMerkleTree)
        // Verify the corresponding txId with every possible way.
        assertTrue(Crypto.doVerify(txId, txSignature))
        assertTrue(txSignature.verify(txId))
        assertTrue(Crypto.isValid(txId, txSignature))
        assertTrue(txSignature.isValid(txId))

        // We signed the txId itself, not its hash (because it was a signature over one tx only and no partial tree has been received).
        assertFailsWith<SignatureException> { Crypto.doVerify(txId.sha256(), txSignature) }
    }

    // Returns a TransactionSignature over the Merkle root, but the partial tree is null.
    private fun signMultipleTx(txIds: List<SecureHash>, keyPair: KeyPair): TransactionSignature {
        val merkleTreeRoot = MerkleTree.getMerkleTree(txIds.map { it.sha256() }).hash
        return signOneTx(merkleTreeRoot, keyPair)
    }

    // Returns a TransactionSignature over one SecureHash.
    // Note that if one tx is to be signed, we don't create a Merkle tree and we directly sign over the txId.
    private fun signOneTx(txId: SecureHash, keyPair: KeyPair): TransactionSignature {
        val signableData = SignableData(txId, SignatureMetadata(3, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))
        return keyPair.sign(signableData)
    }

    @Test
    fun `Sign wrong metadata scheme`() {
        val txID = testBytes.sha256()

        // Test for ECDSA-R1, using a wrong Metadata scheme (intended for ECDSA K1).
        val keyPairECDSAR1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        assertEquals(3, Crypto.findSignatureScheme(keyPairECDSAR1.public).schemeNumberID)

        // Create a SignableData object.
        val k1SchemeNumberID = 2
        val signableDataForK1 = SignableData(txID, SignatureMetadata(1, k1SchemeNumberID))

        // Sign the meta object.
        assertFailsWith<IllegalArgumentException> { keyPairECDSAR1.sign(signableDataForK1) }

        // Test for EdDSA ed25519, using a wrong Metadata scheme (intended for RSA).
        val keyPairEdDSA = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        assertEquals(4, Crypto.findSignatureScheme(keyPairEdDSA.public).schemeNumberID)

        // Create a SignableData object.
        val rsaSchemeNumberID = 1
        val signableDataEdDSA = SignableData(txID, SignatureMetadata(1, rsaSchemeNumberID))

        // Sign the meta object.
        assertFailsWith<IllegalArgumentException> { keyPairEdDSA.sign(signableDataEdDSA) }
    }

    @Test
    fun `Verify wrong metadata scheme`() {
        val txID = testBytes.sha256()

        // Test for ECDSA-R1, using a wrong Metadata scheme (intended for ECDSA K1).
        val keyPairECDSAR1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)

        // Create a SignableData object.
        val k1SchemeNumberID = 2
        val signableDataForK1 = SignableData(txID, SignatureMetadata(1, k1SchemeNumberID))

        // Sign the meta object.
        // Not using keyPair.sign(signableData) or doSign(keyPair, signableData) because
        // it won't let me sign a wrong Metadata object; we are only testing verification of potentially malicious Metadata.
        val signatureBytesR1 = Crypto.doSign(Crypto.findSignatureScheme(keyPairECDSAR1.private), keyPairECDSAR1.private, signableDataForK1.serialize().bytes)
        val txSignatureR1 = TransactionSignature(signatureBytesR1, keyPairECDSAR1.public, signableDataForK1.signatureMetadata)
        assertFailsWith<IllegalArgumentException> { Crypto.doVerify(txID, txSignatureR1) }

        // Test for EdDSA ed25519, using a wrong Metadata scheme (intended for RSA).
        val keyPairEdDSA = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)

        // Create a SignableData object.
        val rsaSchemeNumberID = 1
        val signableDataEdDSA = SignableData(txID, SignatureMetadata(1, rsaSchemeNumberID))

        // Sign the meta object.
        val signatureBytesEdDSA = Crypto.doSign(Crypto.findSignatureScheme(keyPairEdDSA.private), keyPairEdDSA.private, signableDataEdDSA.serialize().bytes)
        val txSignatureEdDSA = TransactionSignature(signatureBytesEdDSA, keyPairEdDSA.public, signableDataEdDSA.signatureMetadata)
        assertFailsWith<IllegalArgumentException> { Crypto.doVerify(txID, txSignatureEdDSA) }
    }
}

package net.corda.coretests.crypto

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.crypto.PartialMerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProof
import net.corda.core.crypto.keyrotation.crossprovider.KeyRotationProofChain
import net.corda.core.crypto.sha256
import net.corda.core.crypto.sign
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.core.SerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.SignatureException
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
    @Test(timeout=300_000)
	fun `Signature metadata full sign and verify`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")

        // Create a SignableData object.
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))

        // Sign the meta object.
        val transactionSignature: TransactionSignature = keyPair.sign(signableData)

        // Check auto-verification.
        assertTrue(transactionSignature.verify(testBytes.sha256()))

        // Check manual verification.
        assertTrue(Crypto.doVerify(testBytes.sha256(), transactionSignature))
    }

    @Test(timeout=300_000)
	fun `Signature metadata preserves legacy serialization format`() {
        val keyPair = loadKeyPair()
        val expectedSignatureMetadataSerializedBytes = File("src/test/resources/corda-414/signature-metadata/serialized", "signature-metadata.bin").readBytes()
        assertTrue(
                expectedSignatureMetadataSerializedBytes.contentEquals(SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID).serialize().bytes),
                "SignatureMetadata serialization format has changed."
        )
    }

    @Test(timeout=300_000)
	fun `Signature remains unchanged from earlier corda versions`() {
        val keyPair = loadKeyPair()
        val expectedSignableDataSerializedBytes = File("src/test/resources/corda-414/signable-data/serialized", "signable-data.bin").readBytes()
        val expectedSignableDataSignatureBytes = File("src/test/resources/corda-414/signable-data/signature", "signable-data.sig").readBytes()

        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))

        assertTrue(
                expectedSignableDataSerializedBytes.contentEquals(signableData.serialize().bytes),
                "Signable data serialization format has changed."
        )

        val transactionSignature: TransactionSignature = keyPair.sign(signableData)

        assertTrue(
                expectedSignableDataSignatureBytes.contentEquals(transactionSignature.bytes),
                "The signature has changed."
        )

        assertTrue(transactionSignature.verify(testBytes.sha256()))
        assertTrue(Crypto.doVerify(testBytes.sha256(), transactionSignature))
    }

    /** Valid sign and verify with key rotation proof. */
    @Test(timeout=300_000)
    fun `Signature metadata full sign and verify with key rotation proof`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val newKeyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val proof = createProof(keyPair, newKeyPair.public)

        // Create a SignableData object.
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(newKeyPair.public).schemeNumberID, KeyRotationProofChain(listOf(proof))))

        // Sign the meta object.
        val transactionSignature: TransactionSignature = newKeyPair.sign(signableData)

        // Check auto-verification.
        assertTrue(transactionSignature.verify(testBytes.sha256()))

        // Check manual verification.
        assertTrue(Crypto.doVerify(testBytes.sha256(), transactionSignature))
    }

    /** Verification should fail; corrupted metadata - clearData (Merkle root) has changed. */
    @Test(timeout=300_000)
    fun `Signature metadata full failure clearData has changed`() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))
        val transactionSignature = keyPair.sign(signableData)
        assertThatExceptionOfType(SignatureException::class.java).isThrownBy {
            Crypto.doVerify((testBytes + testBytes).sha256(), transactionSignature)
        }
    }

    /** Verification should fail if the proof contains an invalid signature. */
    @Test(timeout=300_000)
    fun `Signature verification should fail if key rotation proof is tampered `() {
        val keyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val newKeyPair = Crypto.generateKeyPair("ECDSA_SECP256K1_SHA256")
        val proof = createProof(keyPair, newKeyPair.public)
        val tempProof = createProof(newKeyPair, newKeyPair.public)
        val tamperedProof = proof.copy(signature = tempProof.signature)

        // Create a SignableData object.
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(newKeyPair.public).schemeNumberID, KeyRotationProofChain(listOf(tamperedProof))))

        // Sign the meta object.
        val transactionSignature: TransactionSignature = newKeyPair.sign(signableData)

        // Check auto-verification.
        assertFailsWith<SignatureException> { transactionSignature.verify(testBytes.sha256()) }

        // Check manual verification.
        assertFailsWith<SignatureException> { Crypto.doVerify(testBytes.sha256(), transactionSignature) }
    }

    @Test(timeout=300_000)
	fun `Verify multi-tx signature`() {
        val keyPair = Crypto.deriveKeyPairFromEntropy(Crypto.EDDSA_ED25519_SHA512, BigInteger.valueOf(1234567890L))

        // Deterministically create 5 txIds.
        val txIds: List<SecureHash> = IntRange(0, 4).map { byteArrayOf(it.toByte()).sha256() }
        // Multi-tx signature.
        val txSignature = signMultipleTx(txIds, keyPair)

        // The hash of all txIds are used as leaves.
        val merkleTree = MerkleTree.getMerkleTree(txIds.map { it.reHash() })

        // We haven't added the partial tree yet.
        assertNull(txSignature.partialMerkleTree)
        // Because partial tree is still null, but we signed over a block of txs, verifying a single tx will fail.
        assertFailsWith<SignatureException> { Crypto.doVerify(txIds[3], txSignature) }

        // Create a partial tree for one tx.
        val pmt = PartialMerkleTree.build(merkleTree, listOf(txIds[0].reHash()))
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
        val pmtFull = PartialMerkleTree.build(merkleTree, txIds.map { it.reHash() })
        // Add the partial Merkle tree to the tx.
        val txSignatureWithFullTree = TransactionSignature(txSignature.bytes, txSignature.by, txSignature.signatureMetadata, pmtFull)

        // All txs can be verified, as they are all included in the provided partial tree.
        txIds.forEach {
            assertTrue(Crypto.doVerify(it, txSignatureWithFullTree))
        }
    }

    @Test(timeout=300_000)
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

    /*
    *  This test ensures that Corda Open Source can verify signatures of transactions that have a proof chain, which is a feature
    *  introduced in Corda Enterprise for cross-provider key rotation. The test loads a pre-signed transaction with a key rotation
    *  proof from a binary file and checks that at least one signature has a proof chain. It then verifies that the open source
    * version of Corda can successfully validate the signatures of the transaction, demonstrating compatibility with the key
    *  rotation feature.
    */
    @Test(timeout = 300_000)
    fun verifyCordaOSCanVerifyTransactionSignaturesAfterCrossProviderKeyRotation() {
        val inputStream = javaClass.classLoader.getResourceAsStream("corda-415/transaction_with_key_rotation_proof.bin")
        assertNotNull(inputStream, "Failed to load the signed transaction from the resource file.")

        val loadedTx = inputStream.readBytes() .deserialize<SignedTransaction>()

        // make sure at least one signature has a proof chain
        val loadedSig = loadedTx.sigs.filter { it.signatureMetadata.proofChain != null }
        assertTrue(loadedSig.isNotEmpty(), "At least one signature must have a proof chain")

        // make sure that the open source can verify the signature of a transaction that has a proof chain
        assertDoesNotThrow { loadedTx.checkSignaturesAreValid() }
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

    private fun createProof(oldKeyPair: KeyPair, newPublicKey: java.security.PublicKey): KeyRotationProof {
        val signature = Crypto.doSign(oldKeyPair.private, newPublicKey.encoded)
        return KeyRotationProof(oldKeyPair.public, newPublicKey, signature)
    }

    private fun loadKeyPair(): KeyPair {
        val publicKeyFile = File("src/test/resources/test-keys", "test-key.public")
        val privateKeyFile = File("src/test/resources/test-keys", "test-key.private")

        require(publicKeyFile.exists()) { "Public key file does not exist. File: ${publicKeyFile.absolutePath}" }
        require(privateKeyFile.exists()) { "Private key file does not exist. File: ${privateKeyFile.absolutePath}" }

        return KeyPair(
                Crypto.decodePublicKey(publicKeyFile.readBytes()),
                Crypto.decodePrivateKey(privateKeyFile.readBytes())
        )
    }
}

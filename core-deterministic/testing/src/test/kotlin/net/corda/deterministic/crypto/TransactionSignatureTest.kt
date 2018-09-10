package net.corda.deterministic.crypto

import net.corda.core.crypto.*
import net.corda.deterministic.KeyStoreProvider
import net.corda.deterministic.CheatingSecurityProvider
import net.corda.deterministic.verifier.LocalSerializationRule
import org.junit.*
import org.junit.rules.RuleChain
import java.security.*
import kotlin.test.*

class TransactionSignatureTest {
    companion object {
        private const val KEYSTORE_PASSWORD = "deterministic"
        private val testBytes = "12345678901234567890123456789012".toByteArray()

        private val keyStoreProvider = KeyStoreProvider("keystore/txsignature.pfx", KEYSTORE_PASSWORD)
        private lateinit var keyPair: KeyPair

        @ClassRule
        @JvmField
        val rules: RuleChain = RuleChain.outerRule(LocalSerializationRule(TransactionSignatureTest::class))
            .around(keyStoreProvider)

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            keyPair = keyStoreProvider.getKeyPair("tx")
        }
    }

    /** Valid sign and verify. */
    @Test
    fun `Signature metadata full sign and verify`() {
        // Create a SignableData object.
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))

        // Sign the meta object.
        val transactionSignature: TransactionSignature = CheatingSecurityProvider().use {
            keyPair.sign(signableData)
        }

        // Check auto-verification.
        assertTrue(transactionSignature.verify(testBytes.sha256()))

        // Check manual verification.
        assertTrue(Crypto.doVerify(testBytes.sha256(), transactionSignature))
    }

    /** Verification should fail; corrupted metadata - clearData (Merkle root) has changed. */
    @Test(expected = SignatureException::class)
    fun `Signature metadata full failure clearData has changed`() {
        val signableData = SignableData(testBytes.sha256(), SignatureMetadata(1, Crypto.findSignatureScheme(keyPair.public).schemeNumberID))
        val transactionSignature = CheatingSecurityProvider().use {
            keyPair.sign(signableData)
        }
        Crypto.doVerify((testBytes + testBytes).sha256(), transactionSignature)
    }

    @Test
    fun `Verify multi-tx signature`() {
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
        val txId = "aTransaction".toByteArray().sha256()
        // One-tx signature.
        val txSignature = try {
            signOneTx(txId, keyPair)
        } catch (e: Throwable) {
            e.cause?.printStackTrace()
            throw e
        }

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
        return CheatingSecurityProvider().use {
            keyPair.sign(signableData)
        }
    }
}

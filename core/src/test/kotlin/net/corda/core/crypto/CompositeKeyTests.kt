/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import net.corda.core.crypto.CompositeKey.NodeAndWeight
import net.corda.core.internal.declaredField
import net.corda.core.internal.div
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toBase58String
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.loadKeyStore
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.kryoSpecific
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.PublicKey
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeKeyTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val aliceKey = generateKeyPair()
    private val bobKey = generateKeyPair()
    private val charlieKey = generateKeyPair()

    private val alicePublicKey: PublicKey = aliceKey.public
    private val bobPublicKey: PublicKey = bobKey.public
    private val charliePublicKey: PublicKey = charlieKey.public

    private val message = OpaqueBytes("Transaction".toByteArray())
    private val secureHash = message.sha256()

    // By lazy is required so that the serialisers are configured before vals initialisation takes place (they internally invoke serialise).
    private val aliceSignature by lazy { aliceKey.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(alicePublicKey).schemeNumberID))) }
    private val bobSignature by lazy { bobKey.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(bobPublicKey).schemeNumberID))) }
    private val charlieSignature by lazy { charlieKey.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(charliePublicKey).schemeNumberID))) }

    @Test
    fun `(Alice) fulfilled by Alice signature`() {
        assertTrue { alicePublicKey.isFulfilledBy(aliceSignature.by) }
        assertFalse { alicePublicKey.isFulfilledBy(charlieSignature.by) }
    }

    @Test
    fun `(Alice or Bob) fulfilled by either signature`() {
        val aliceOrBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(threshold = 1)
        assertTrue { aliceOrBob.isFulfilledBy(aliceSignature.by) }
        assertTrue { aliceOrBob.isFulfilledBy(bobSignature.by) }
        assertTrue { aliceOrBob.isFulfilledBy(listOf(aliceSignature.by, bobSignature.by)) }
        assertFalse { aliceOrBob.isFulfilledBy(charlieSignature.by) }
    }

    @Test
    fun `(Alice and Bob) fulfilled by Alice, Bob signatures`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val signatures = listOf(aliceSignature, bobSignature)
        assertTrue { aliceAndBob.isFulfilledBy(signatures.byKeys()) }
    }

    @Test
    fun `(Alice and Bob) requires both signatures to fulfil`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        assertFalse { aliceAndBob.isFulfilledBy(listOf(aliceSignature).byKeys()) }
        assertFalse { aliceAndBob.isFulfilledBy(listOf(bobSignature).byKeys()) }
        assertTrue { aliceAndBob.isFulfilledBy(listOf(aliceSignature, bobSignature).byKeys()) }
    }

    @Test
    fun `((Alice and Bob) or Charlie) signature verifies`() {
        // TODO: Look into a DSL for building multi-level composite keys if that becomes a common use case
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val signatures = listOf(aliceSignature, bobSignature)

        assertTrue { aliceAndBobOrCharlie.isFulfilledBy(signatures.byKeys()) }
    }

    @Test
    fun `encoded tree decodes correctly`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val encoded = aliceAndBobOrCharlie.toBase58String()
        val decoded = net.corda.core.utilities.parsePublicKeyBase58(encoded)

        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @Test
    fun `der encoded tree decodes correctly`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val encoded = aliceAndBobOrCharlie.encoded
        val decoded = CompositeKey.getInstance(encoded)

        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @Test
    fun `der encoded tree decodes correctly with weighting`() {
        val aliceAndBob = CompositeKey.Builder()
                .addKey(alicePublicKey, 2)
                .addKey(bobPublicKey, 1)
                .build(threshold = 2)

        val aliceAndBobOrCharlie = CompositeKey.Builder()
                .addKey(aliceAndBob, 3)
                .addKey(charliePublicKey, 2)
                .build(threshold = 3)

        val encoded = aliceAndBobOrCharlie.encoded
        val decoded = CompositeKey.getInstance(encoded)

        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @Test
    fun `tree canonical form`() {
        assertEquals(CompositeKey.Builder().addKeys(alicePublicKey).build(), alicePublicKey)
        val node1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(1) // threshold = 1
        val node2 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(2) // threshold = 2
        assertFalse(node2.isFulfilledBy(alicePublicKey))
        // Ordering by weight.
        val tree1 = CompositeKey.Builder().addKey(node1, 13).addKey(node2, 27).build()
        val tree2 = CompositeKey.Builder().addKey(node2, 27).addKey(node1, 13).build()
        assertEquals(tree1, tree2)
        assertEquals(tree1.hashCode(), tree2.hashCode())

        // Ordering by node, weights the same.
        val tree3 = CompositeKey.Builder().addKeys(node1, node2).build()
        val tree4 = CompositeKey.Builder().addKeys(node2, node1).build()
        assertEquals(tree3, tree4)
        assertEquals(tree3.hashCode(), tree4.hashCode())

        // Duplicate node cases.
        val tree5 = CompositeKey.Builder().addKey(node1, 3).addKey(node1, 14).build()
        val tree6 = CompositeKey.Builder().addKey(node1, 14).addKey(node1, 3).build()
        assertEquals(tree5, tree6)

        // Chain of single nodes should throw.
        assertEquals(CompositeKey.Builder().addKeys(tree1).build(), tree1)
    }

    /**
     * Check that verifying a composite signature using the [CompositeSignature] engine works.
     */
    @Test
    fun `composite TransactionSignature verification `() {
        val twoOfThree = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey, charliePublicKey).build(threshold = 2)

        val engine = CompositeSignature()
        engine.initVerify(twoOfThree)
        engine.update(secureHash.bytes)

        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature)).serialize().bytes) }
        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(bobSignature)).serialize().bytes) }
        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, bobSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(bobSignature, charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, bobSignature, charlieSignature)).serialize().bytes) }

        // Check the underlying signature is validated
        val brokenBobSignature = TransactionSignature(aliceSignature.bytes, bobSignature.by, SignatureMetadata(1, Crypto.findSignatureScheme(bobSignature.by).schemeNumberID))
        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, brokenBobSignature)).serialize().bytes) }
    }

    @Test()
    fun `composite key constraints`() {
        // Zero weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 0)
        }
        // Negative weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, -1)
        }
        // Zero threshold.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey).build(0)
        }
        // Negative threshold.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey).build(-1)
        }
        // Threshold > Total-weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 2).addKey(bobPublicKey, 2).build(5)
        }
        // Threshold value different than weight of single child node.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 3).build(2)
        }
        // Aggregated weight integer overflow.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, Int.MAX_VALUE).addKey(bobPublicKey, Int.MAX_VALUE).build()
        }
        // Duplicated children.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey, alicePublicKey).build()
        }
        // Duplicated composite key children.
        assertFailsWith(IllegalArgumentException::class) {
            val compositeKey1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
            val compositeKey2 = CompositeKey.Builder().addKeys(bobPublicKey, alicePublicKey).build()
            CompositeKey.Builder().addKeys(compositeKey1, compositeKey2).build()
        }
    }

    @Test()
    fun `composite key validation with graph cycle detection`() = kryoSpecific("Cycle exists in the object graph which is not currently supported in AMQP mode") {
        val key1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build() as CompositeKey
        val key2 = CompositeKey.Builder().addKeys(alicePublicKey, key1).build() as CompositeKey
        val key3 = CompositeKey.Builder().addKeys(alicePublicKey, key2).build() as CompositeKey
        val key4 = CompositeKey.Builder().addKeys(alicePublicKey, key3).build() as CompositeKey
        val key5 = CompositeKey.Builder().addKeys(alicePublicKey, key4).build() as CompositeKey
        val key6 = CompositeKey.Builder().addKeys(alicePublicKey, key5, key2).build() as CompositeKey

        // Initially, there is no any graph cycle.
        key1.checkValidity()
        key2.checkValidity()
        key3.checkValidity()
        key4.checkValidity()
        key5.checkValidity()
        // The fact that key6 has a direct reference to key2 and an indirect (via path key5->key4->key3->key2)
        // does not imply a cycle, as expected (independent paths).
        key6.checkValidity()

        // We will create a graph cycle between key5 and key3. Key5 has already a reference to key3 (via key4).
        // To create a cycle, we add a reference (child) from key3 to key5.
        // Children list is immutable, so reflection is used to inject key5 as an extra NodeAndWeight child of key3.
        key3.declaredField<List<NodeAndWeight>>("children").value = key3.children + NodeAndWeight(key5, 1)

        /* A view of the example graph cycle.
         *
         *               key6
         *              /    \
         *            key5   key2
         *            /
         *         key4
         *         /
         *       key3
         *      /   \
         *    key2  key5
         *    /
         *  key1
         *
         */

        // Detect the graph cycle starting from key3.
        assertFailsWith(IllegalArgumentException::class) {
            key3.checkValidity()
        }

        // Detect the graph cycle starting from key4.
        assertFailsWith(IllegalArgumentException::class) {
            key4.checkValidity()
        }

        // Detect the graph cycle starting from key5.
        assertFailsWith(IllegalArgumentException::class) {
            key5.checkValidity()
        }

        // Detect the graph cycle starting from key6.
        // Typically, one needs to test on the root tree-node only (thus, a validity check on key6 would be enough).
        assertFailsWith(IllegalArgumentException::class) {
            key6.checkValidity()
        }

        // Key2 (and all paths below it, i.e. key1) are outside the graph cycle and thus, there is no impact on them.
        key2.checkValidity()
        key1.checkValidity()
    }

    @Test
    fun `CompositeKey from multiple signature schemes and signature verification`() {
        val keyPairRSA = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val keyPairK1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val keyPairR1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val keyPairEd = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val keyPairSP = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)

        val RSASignature = keyPairRSA.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairRSA.public).schemeNumberID)))
        val K1Signature = keyPairK1.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairK1.public).schemeNumberID)))
        val R1Signature = keyPairR1.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairR1.public).schemeNumberID)))
        val EdSignature = keyPairEd.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairEd.public).schemeNumberID)))
        val SPSignature = keyPairSP.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairSP.public).schemeNumberID)))

        val compositeKey = CompositeKey.Builder().addKeys(keyPairRSA.public, keyPairK1.public, keyPairR1.public, keyPairEd.public, keyPairSP.public).build() as CompositeKey

        val signatures = listOf(RSASignature, K1Signature, R1Signature, EdSignature, SPSignature)
        assertTrue { compositeKey.isFulfilledBy(signatures.byKeys()) }

        // One signature is missing.
        val signaturesWithoutRSA = listOf(K1Signature, R1Signature, EdSignature, SPSignature)
        assertFalse { compositeKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }
    }

    @Test
    fun `Test save to keystore`() {
        // From test case [CompositeKey from multiple signature schemes and signature verification]
        val keyPairRSA = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val keyPairK1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val keyPairR1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val keyPairEd = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val keyPairSP = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)

        val RSASignature = keyPairRSA.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairRSA.public).schemeNumberID)))
        val K1Signature = keyPairK1.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairK1.public).schemeNumberID)))
        val R1Signature = keyPairR1.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairR1.public).schemeNumberID)))
        val EdSignature = keyPairEd.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairEd.public).schemeNumberID)))
        val SPSignature = keyPairSP.sign(SignableData(secureHash, SignatureMetadata(1, Crypto.findSignatureScheme(keyPairSP.public).schemeNumberID)))

        val compositeKey = CompositeKey.Builder().addKeys(keyPairRSA.public, keyPairK1.public, keyPairR1.public, keyPairEd.public, keyPairSP.public).build() as CompositeKey

        val signatures = listOf(RSASignature, K1Signature, R1Signature, EdSignature, SPSignature)
        assertTrue { compositeKey.isFulfilledBy(signatures.byKeys()) }
        // One signature is missing.
        val signaturesWithoutRSA = listOf(K1Signature, R1Signature, EdSignature, SPSignature)
        assertFalse { compositeKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }

        // Create self sign CA.
        val caKeyPair = Crypto.generateKeyPair()
        val caName = X500Principal("CN=Test CA,O=R3 Ltd,L=London,C=GB")
        val ca = X509Utilities.createSelfSignedCACertificate(caName, caKeyPair)

        // Sign the composite key with the self sign CA.
        val compositeKeyCert = X509Utilities.createCertificate(CertificateType.LEGAL_IDENTITY, ca, caKeyPair, caName, compositeKey)

        // Store certificate to keystore.
        val keystorePath = tempFolder.root.toPath() / "keystore.jks"
        X509KeyStore.fromFile(keystorePath, "password", createNew = true).update {
            setCertificate("CompositeKey", compositeKeyCert)
        }

        // Load keystore from disk.
        val keystore2 = loadKeyStore(keystorePath, "password")
        assertTrue { keystore2.containsAlias("CompositeKey") }

        val key = keystore2.getCertificate("CompositeKey").publicKey
        // Convert sun public key to Composite key.
        val compositeKey2 = Crypto.toSupportedPublicKey(key)
        assertThat(compositeKey2).isInstanceOf(CompositeKey::class.java)

        // Run the same composite key test again.
        assertTrue { compositeKey2.isFulfilledBy(signatures.byKeys()) }
        assertFalse { compositeKey2.isFulfilledBy(signaturesWithoutRSA.byKeys()) }

        // Ensure keys are the same before and after keystore.
        assertEquals(compositeKey, compositeKey2)
    }

    @Test
    fun `CompositeKey deterministic children sorting`() {
        val (_, pub1) = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (_, pub2) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val (_, pub3) = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val (_, pub4) = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (_, pub5) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val (_, pub6) = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)
        val (_, pub7) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)

        // Using default weight = 1, thus all weights are equal.
        val composite1 = CompositeKey.Builder().addKeys(pub1, pub2, pub3, pub4, pub5, pub6, pub7).build() as CompositeKey
        // Store in reverse order.
        val composite2 = CompositeKey.Builder().addKeys(pub7, pub6, pub5, pub4, pub3, pub2, pub1).build() as CompositeKey
        // There are 7! = 5040 permutations, but as sorting is deterministic the following should never fail.
        assertEquals(composite1.children, composite2.children)
    }
}

package net.corda.core.crypto

import net.corda.core.crypto.composite.CompositeKey
import net.corda.core.crypto.composite.CompositeSignature
import net.corda.core.crypto.composite.CompositeSignaturesWithKeys
import net.corda.core.div
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeKeyTests {
    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    val aliceKey = generateKeyPair()
    val bobKey = generateKeyPair()
    val charlieKey = generateKeyPair()

    val alicePublicKey = aliceKey.public
    val bobPublicKey = bobKey.public
    val charliePublicKey = charlieKey.public

    val message = OpaqueBytes("Transaction".toByteArray())

    val aliceSignature = aliceKey.sign(message)
    val bobSignature = bobKey.sign(message)
    val charlieSignature = charlieKey.sign(message)

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
    fun `kryo encoded tree decodes correctly`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val encoded = aliceAndBobOrCharlie.toBase58String()
        val decoded = parsePublicKeyBase58(encoded)

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
    fun `composite signature verification`() {
        val twoOfThree = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey, charliePublicKey).build(threshold = 2)
        val engine = CompositeSignature()
        engine.initVerify(twoOfThree)
        engine.update(message.bytes)

        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature)).serialize().bytes) }
        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(bobSignature)).serialize().bytes) }
        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, bobSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(bobSignature, charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, bobSignature, charlieSignature)).serialize().bytes) }

        // Check the underlying signature is validated
        val brokenBobSignature = DigitalSignature.WithKey(bobSignature.by, aliceSignature.bytes)
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
    fun `composite key validation with graph cycle detection`() {
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
        val field = key3.javaClass.getDeclaredField("children")
        field.isAccessible = true
        val fixedChildren = key3.children.plus(CompositeKey.NodeAndWeight(key5, 1))
        field.set(key3, fixedChildren)

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
        val (privRSA, pubRSA) = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val (privK1, pubK1) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val (privR1, pubR1) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val (privEd, pubEd) = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (privSP, pubSP) = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)

        val RSASignature = privRSA.sign(message.bytes, pubRSA)
        val K1Signature = privK1.sign(message.bytes, pubK1)
        val R1Signature = privR1.sign(message.bytes, pubR1)
        val EdSignature = privEd.sign(message.bytes, pubEd)
        val SPSignature = privSP.sign(message.bytes, pubSP)

        val compositeKey = CompositeKey.Builder().addKeys(pubRSA, pubK1, pubR1, pubEd, pubSP).build() as CompositeKey

        val signatures = listOf(RSASignature, K1Signature, R1Signature, EdSignature, SPSignature)
        assertTrue { compositeKey.isFulfilledBy(signatures.byKeys()) }

        // One signature is missing.
        val signaturesWithoutRSA = listOf(K1Signature, R1Signature, EdSignature, SPSignature)
        assertFalse { compositeKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }
    }

    @Test
    fun `Test save to keystore`() {
        // From test case [CompositeKey from multiple signature schemes and signature verification]
        val (privRSA, pubRSA) = Crypto.generateKeyPair(Crypto.RSA_SHA256)
        val (privK1, pubK1) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
        val (privR1, pubR1) = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256)
        val (privEd, pubEd) = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512)
        val (privSP, pubSP) = Crypto.generateKeyPair(Crypto.SPHINCS256_SHA256)

        val RSASignature = privRSA.sign(message.bytes, pubRSA)
        val K1Signature = privK1.sign(message.bytes, pubK1)
        val R1Signature = privR1.sign(message.bytes, pubR1)
        val EdSignature = privEd.sign(message.bytes, pubEd)
        val SPSignature = privSP.sign(message.bytes, pubSP)

        val compositeKey = CompositeKey.Builder().addKeys(pubRSA, pubK1, pubR1, pubEd, pubSP).build() as CompositeKey

        val signatures = listOf(RSASignature, K1Signature, R1Signature, EdSignature, SPSignature)
        assertTrue { compositeKey.isFulfilledBy(signatures.byKeys()) }
        // One signature is missing.
        val signaturesWithoutRSA = listOf(K1Signature, R1Signature, EdSignature, SPSignature)
        assertFalse { compositeKey.isFulfilledBy(signaturesWithoutRSA.byKeys()) }

        // Create self sign CA.
        val caKeyPair = Crypto.generateKeyPair()
        val ca = X509Utilities.createSelfSignedCACertificate(X500Name("CN=Test CA"), caKeyPair)

        // Sign the composite key with the self sign CA.
        val compositeKeyCert = X509Utilities.createCertificate(CertificateType.IDENTITY, ca, caKeyPair, X500Name("CN=CompositeKey"), compositeKey)

        // Store certificate to keystore.
        val keystorePath = tempFolder.root.toPath() / "keystore.jks"
        val keystore = KeyStoreUtilities.loadOrCreateKeyStore(keystorePath, "password")
        keystore.setCertificateEntry("CompositeKey", compositeKeyCert.cert)
        keystore.save(keystorePath, "password")

        // Load keystore from disk.
        val keystore2 = KeyStoreUtilities.loadKeyStore(keystorePath, "password")
        assertTrue { keystore2.containsAlias("CompositeKey") }

        val key = keystore2.getCertificate("CompositeKey").publicKey
        // Convert sun public key to Composite key.
        val compositeKey2 = Crypto.toSupportedPublicKey(key)
        assertTrue { compositeKey2 is CompositeKey }

        // Run the same composite key test again.
        assertTrue { compositeKey2.isFulfilledBy(signatures.byKeys()) }
        assertFalse { compositeKey2.isFulfilledBy(signaturesWithoutRSA.byKeys()) }
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

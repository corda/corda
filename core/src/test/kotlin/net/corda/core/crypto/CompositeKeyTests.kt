package net.corda.core.crypto

import net.corda.core.serialization.OpaqueBytes
import net.corda.core.serialization.serialize
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeKeyTests {
    val aliceKey = generateKeyPair()
    val bobKey = generateKeyPair()
    val charlieKey = generateKeyPair()

    val alicePublicKey = aliceKey.public
    val bobPublicKey = bobKey.public
    val charliePublicKey = charlieKey.public

    val message = OpaqueBytes("Transaction".toByteArray())

    val aliceSignature = aliceKey.signWithECDSA(message)
    val bobSignature = bobKey.signWithECDSA(message)
    val charlieSignature = charlieKey.signWithECDSA(message)
    val compositeAliceSignature = CompositeSignaturesWithKeys(listOf(aliceSignature))

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
        val decoded = parsePublicKeyBase58(encoded)

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
}

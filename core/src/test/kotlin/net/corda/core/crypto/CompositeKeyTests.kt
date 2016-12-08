package net.corda.core.crypto

import net.corda.core.serialization.OpaqueBytes
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositeKeyTests {
    val aliceKey = generateKeyPair()
    val bobKey = generateKeyPair()
    val charlieKey = generateKeyPair()

    val alicePublicKey = CompositeKey.Leaf(aliceKey.public)
    val bobPublicKey = CompositeKey.Leaf(bobKey.public)
    val charliePublicKey = CompositeKey.Leaf(charlieKey.public)

    val message = OpaqueBytes("Transaction".toByteArray())

    val aliceSignature = aliceKey.signWithECDSA(message)
    val bobSignature = bobKey.signWithECDSA(message)

    @Test
    fun `(Alice) fulfilled by Alice signature`() {
        assertTrue { alicePublicKey.isFulfilledBy(aliceSignature.by) }
    }

    @Test
    fun `(Alice or Bob) fulfilled by Bob signature`() {
        val aliceOrBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(threshold = 1)
        assertTrue { aliceOrBob.isFulfilledBy(bobSignature.by) }
    }

    @Test
    fun `(Alice and Bob) fulfilled by Alice, Bob signatures`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val signatures = listOf(aliceSignature, bobSignature)
        assertTrue { aliceAndBob.isFulfilledBy(signatures.byKeys()) }
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
        val decoded = CompositeKey.parseFromBase58(encoded)

        assertEquals(decoded, aliceAndBobOrCharlie)
    }
}

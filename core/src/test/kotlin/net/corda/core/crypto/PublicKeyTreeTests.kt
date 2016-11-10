package net.corda.core.crypto

import net.corda.core.serialization.OpaqueBytes
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicKeyTreeTests {
    val aliceKey = generateKeyPair()
    val bobKey = generateKeyPair()
    val charlieKey = generateKeyPair()

    val alicePublicKey = PublicKeyTree.Leaf(aliceKey.public)
    val bobPublicKey = PublicKeyTree.Leaf(bobKey.public)
    val charliePublicKey = PublicKeyTree.Leaf(charlieKey.public)

    val message = OpaqueBytes("Transaction".toByteArray())

    val aliceSignature = aliceKey.signWithECDSA(message)
    val bobSignature = bobKey.signWithECDSA(message)

    @Test
    fun `(Alice) fulfilled by Alice signature`() {
        assertTrue { alicePublicKey.isFulfilledBy(aliceSignature.by) }
    }

    @Test
    fun `(Alice or Bob) fulfilled by Bob signature`() {
        val aliceOrBob = PublicKeyTree.Builder().addKeys(alicePublicKey, bobPublicKey).build(threshold = 1)
        assertTrue { aliceOrBob.isFulfilledBy(bobSignature.by) }
    }

    @Test
    fun `(Alice and Bob) fulfilled by Alice, Bob signatures`() {
        val aliceAndBob = PublicKeyTree.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val signatures = listOf(aliceSignature, bobSignature)
        assertTrue { aliceAndBob.isFulfilledBy(signatures.byKeys()) }
    }

    @Test
    fun `((Alice and Bob) or Charlie) signature verifies`() {
        // TODO: Look into a DSL for building multi-level public key trees if that becomes a common use case
        val aliceAndBob = PublicKeyTree.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = PublicKeyTree.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val signatures = listOf(aliceSignature, bobSignature)

        assertTrue { aliceAndBobOrCharlie.isFulfilledBy(signatures.byKeys()) }
    }

    @Test
    fun `encoded tree decodes correctly`() {
        val aliceAndBob = PublicKeyTree.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = PublicKeyTree.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val encoded = aliceAndBobOrCharlie.toBase58String()
        val decoded = PublicKeyTree.parseFromBase58(encoded)

        assertEquals(decoded, aliceAndBobOrCharlie)
    }
}

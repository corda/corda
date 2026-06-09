package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.Crypto
import org.junit.Test
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyRotationProofChainTest {

    @Test(timeout=300_000)
    fun `empty chain reports empty and validates only for inferred original equals current path`() {
        val key = generateKeyPair().public
        val chain = KeyRotationProofChain(emptyList())

        assertTrue(chain.isEmpty(), "Expected chain to report empty when initialized with no proofs")
        assertFalse(chain.isNotEmpty(), "Expected chain to report empty when initialized with no proofs")
        assertEquals(0, chain.size(), "Expected chain size to be 0 when initialized with no proofs")
        assertTrue(chain.getKeyLineage().isEmpty(), "Expected key lineage to be empty when initialized with no proofs")
        assertTrue(chain.isValid(key), "Any empty chain should be valid for any key since it implies original and current keys are the same")
        assertFalse(chain.isValid(key, generateKeyPair().public), "Any empty chain should be invalid if original and current keys are different since there is no proof of rotation")
    }

    @Test(timeout=300_000)
    fun `single proof chain exposes origin key, current key, and lineage`() {
        val old = generateKeyPair()
        val new = generateKeyPair()
        val chain = KeyRotationProofChain(listOf(createProof(old, new.public)))

        assertFalse(chain.isEmpty())
        assertTrue(chain.isNotEmpty())
        assertEquals(1, chain.size())
        assertEquals(old.public, chain.originalKey, "Expected original key to match the old key from the proof")
        assertEquals(new.public, chain.currentKey, "Expected current key to match the new key from the proof")
        assertEquals(listOf(old.public, new.public), chain.getKeyLineage(), "Expected key lineage to include both the old and new keys in order")
    }

    @Test(timeout=300_000)
    fun `valid chain with multiple proofs validates against expected original, and current keys`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()
        val k3 = generateKeyPair()

        val chain = KeyRotationProofChain(
            listOf(
                createProof(k1, k2.public),
                createProof(k2, k3.public)
            )
        )

        assertFalse(chain.isEmpty())
        assertTrue(chain.isNotEmpty())
        assertEquals(2, chain.size())
        assertEquals(k1.public, chain.originalKey, "Expected original key to match the old key from the proof")
        assertEquals(k3.public, chain.currentKey, "Expected current key to match the new key from the proof")
        assertEquals(listOf(k1.public, k2.public, k3.public), chain.getKeyLineage(), "Expected key lineage to include both the old and new keys in order")
        assertTrue(chain.isValid(k1.public, k3.public), "Expected chain to be valid when validating against the original key and the current key")
        assertTrue(chain.isValid(k3.public), "The chain is expected to be valid without specifying the original key, as the proofs in the chain should establish a link from the original key to the current key.")
    }

    @Test(timeout=300_000)
    fun `chain fails validation when expected old and new keys do not match`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()
        val k3 = generateKeyPair()
        val other = generateKeyPair()

        val chain = KeyRotationProofChain(
            listOf(
                createProof(k1, k2.public),
                createProof(k2, k3.public)
            )
        )

        assertFalse(chain.isValid(other.public, k3.public))
        assertFalse(chain.isValid(k1.public, other.public))
        assertFalse(chain.isValid(other.public))
    }

    @Test(timeout=300_000)
    fun `chain fails validation when continuity is broken`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()
        val k3 = generateKeyPair()
        val k4 = generateKeyPair()

        val chain = KeyRotationProofChain(
            listOf(
                createProof(k1, k2.public),
                createProof(k3, k4.public)
            )
        )

        assertFalse(chain.isValid(k1.public, k4.public))
        assertFalse(chain.isValid(k4.public))
    }

    @Test(timeout=300_000)
    fun `chain fails validation when unrelated proof appears at beginning`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()
        val k3 = generateKeyPair()
        val k4 = generateKeyPair()
        val unrelatedProof = createProof(k1, k4.public)

        val chain = KeyRotationProofChain(
                listOf(
                        unrelatedProof,
                        createProof(k1, k2.public),
                        createProof(k2, k3.public),
                        createProof(k3, k4.public)
                )
        )

        assertFalse(chain.isValid(k1.public, k4.public))
        assertFalse(chain.isValid(k4.public))
    }

    @Test(timeout=300_000)
    fun `chain fails validation when unrelated proof appears in the middle`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()
        val k3 = generateKeyPair()
        val k4 = generateKeyPair()
        val unrelatedProof = createProof(k1, k4.public)

        val chain = KeyRotationProofChain(
                listOf(
                        createProof(k1, k2.public),
                        createProof(k2, k3.public),
                        unrelatedProof,
                        createProof(k3, k4.public)
                )
        )

        assertFalse(chain.isValid(k1.public, k4.public))
        assertFalse(chain.isValid(k4.public))
    }

    @Test(timeout=300_000)
    fun `chain fails validation when unrelated proof appears at the end`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()
        val k3 = generateKeyPair()
        val k4 = generateKeyPair()
        val unrelatedProof = createProof(k1, k4.public)

        val chain = KeyRotationProofChain(
                listOf(
                        createProof(k1, k2.public),
                        createProof(k2, k3.public),
                        createProof(k3, k4.public),
                        unrelatedProof
                )
        )

        assertFalse(chain.isValid(k1.public, k4.public))
        assertFalse(chain.isValid(k4.public))
    }

    @Test(timeout=300_000)
    fun `chain fails validation when a proof signature is tampered`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()

        val validProof = createProof(k1, k2.public)
        val tampered = validProof.copy(signature = validProof.signature.clone().also { it[0] = (it[0].toInt() xor 0x01).toByte() })
        val chain = KeyRotationProofChain(listOf(tampered))

        assertFalse(chain.isValid(k1.public, k2.public))
        assertFalse(chain.isValid(k2.public))
    }

    private fun createProof(oldKeyPair: java.security.KeyPair, newKey: PublicKey): KeyRotationProof {
        val signature = Crypto.doSign(oldKeyPair.private, newKey.encoded)
        return KeyRotationProof(oldKeyPair.public, newKey, signature)
    }

    private fun generateKeyPair() = Crypto.generateKeyPair()
}

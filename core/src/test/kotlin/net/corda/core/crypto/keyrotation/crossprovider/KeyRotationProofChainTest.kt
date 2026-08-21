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

    @Test(timeout=300_000)
    fun `self-proof where a key rotates to itself is invalid`() {
        val k1 = generateKeyPair()

        // A -> A: a validly-signed proof, but a key cannot rotate to itself.
        val chain = KeyRotationProofChain(listOf(createProof(k1, k1.public)))

        assertFalse(chain.isValid(k1.public, k1.public), "A self-proof A -> A must be rejected")
        assertFalse(chain.isValid(k1.public), "A self-proof A -> A must be rejected via the single-key overload")
    }

    @Test(timeout=300_000)
    fun `chain that loops back to the original key is invalid`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()

        // A -> B -> A: every link is validly signed and continuous, but A is seen twice.
        val chain = KeyRotationProofChain(
            listOf(
                createProof(k1, k2.public),
                createProof(k2, k1.public)
            )
        )

        assertFalse(chain.isValid(k1.public, k1.public), "A loop A -> B -> A must be rejected")
        assertFalse(chain.isValid(k1.public), "A loop A -> B -> A must be rejected via the single-key overload")
    }

    @Test(timeout=300_000)
    fun `chain that revisits an intermediate key is invalid`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()
        val k3 = generateKeyPair()

        // A -> B -> C -> B: continuous and validly signed, but B is seen twice.
        val chain = KeyRotationProofChain(
            listOf(
                createProof(k1, k2.public),
                createProof(k2, k3.public),
                createProof(k3, k2.public)
            )
        )

        assertFalse(chain.isValid(k1.public, k2.public), "A chain revisiting an intermediate key must be rejected")
        assertFalse(chain.isValid(k2.public), "A chain revisiting an intermediate key must be rejected via the single-key overload")
    }

    @Test(timeout=300_000)
    fun `isValid does not short-circuit when the original and current key are the same`() {
        val k1 = generateKeyPair()
        val k2 = generateKeyPair()

        // A genuine, fully valid rotation A -> B.
        val chain = KeyRotationProofChain(listOf(createProof(k1, k2.public)))

        // Sanity: still valid for its real endpoints.
        assertTrue(chain.isValid(k1.public, k2.public), "A genuine A -> B rotation must remain valid")

        // Passing the same key as original and current must NOT return true.
        assertFalse(chain.isValid(k1.public, k1.public), "isValid(originalKey, originalKey) must not short-circuit to true")
        assertFalse(chain.isValid(k1.public), "isValid(originalKey) must not short-circuit to true")
    }

    private fun createProof(oldKeyPair: java.security.KeyPair, newKey: PublicKey): KeyRotationProof {
        val signature = Crypto.doSign(oldKeyPair.private, newKey.encoded)
        return KeyRotationProof(oldKeyPair.public, newKey, signature)
    }

    private fun generateKeyPair() = Crypto.generateKeyPair()
}

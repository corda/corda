package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair

class PartyIdentityResolvedTest {

    @Test
    fun `without proof chain returns original party identity and key`() {
        val original = newParty("Alice")
        val resolved = PartyIdentityResolved(original, proofChain = null as KeyRotationProofChain?)

        assertFalse(resolved.containsProof())
        assertEquals(original.owningKey, resolved.getOriginalKey())
        assertEquals(original.owningKey, resolved.getOwningKey())
        assertEquals(original.name.toString(), resolved.getLegalName())
        assertEquals(original, resolved.originalOrCurrentParty)
        assertEquals(original.owningKey, resolved.originalOrCurrentParty.owningKey)
    }

    @Test
    fun `empty proof chain is treated as no proof`() {
        val original = newParty("Bob")
        val resolved = PartyIdentityResolved(original, KeyRotationProofChain(emptyList()))

        assertFalse(resolved.containsProof())
        assertEquals(original.owningKey, resolved.getOriginalKey())
        assertEquals(original.owningKey, resolved.getOwningKey())
        assertEquals(original.name.toString(), resolved.getLegalName())
        assertEquals(original, resolved.originalOrCurrentParty)
        assertEquals(original.owningKey, resolved.originalOrCurrentParty.owningKey)
    }

    @Test
    fun `non-empty proof chain returns rotated owning key and projected party`() {
        val oldKey = newKeyPair()
        val newKey = newKeyPair()
        val original = Party(CordaX500Name("Charlie", "London", "GB"), oldKey.public)

        val proof = createProof(oldKey, newKey.public)
        val resolved = PartyIdentityResolved(original, KeyRotationProofChain(listOf(proof)))

        assertTrue(resolved.containsProof())
        assertEquals(oldKey.public, resolved.getOriginalKey())
        assertEquals(newKey.public, resolved.getOwningKey())
        assertEquals(original.name.toString(), resolved.getLegalName())
        assertEquals(original.name, resolved.originalOrCurrentParty.name)
        assertEquals(newKey.public, resolved.originalOrCurrentParty.owningKey)
    }

    @Test
    fun `list constructor wraps proof list into chain`() {
        val oldKey = newKeyPair()
        val newKey = newKeyPair()
        val original = Party(CordaX500Name("Dave", "New York", "US"), oldKey.public)

        val proof = createProof(oldKey, newKey.public)
        val resolved = PartyIdentityResolved(original, listOf(proof))

        assertTrue(resolved.containsProof())
        assertEquals(newKey.public, resolved.getOwningKey())
    }

    private fun newParty(commonName: String): Party {
        return Party(CordaX500Name(commonName, "London", "GB"), newKeyPair().public)
    }

    private fun newKeyPair(): KeyPair = Crypto.generateKeyPair()

    private fun createProof(oldKeyPair: KeyPair, newPublicKey: java.security.PublicKey): KeyRotationProof {
        val signature = Crypto.doSign(oldKeyPair.private, newPublicKey.encoded)
        return KeyRotationProof(oldKeyPair.public, newPublicKey, signature)
    }
}
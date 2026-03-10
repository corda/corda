package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.PublicKey

class PartyIdentityResolverTest {

    @Test
    fun `the resolved party is the same as the original party if a proof does not exist`() {

        val originalPairKey = newKeyPair()
        val originalParty = newParty("Alice", originalPairKey.public)
        val proofsByOriginalKeyMap = emptyMap<PublicKey, KeyRotationProofChain>()
        val resolver = PartyIdentityResolver(proofsByOriginalKeyMap)
        val resolvedParty = resolver.resolve(newParty("Alice", originalPairKey.public))

        assertFalse(resolvedParty.containsProof())
        assertEquals(originalParty.owningKey, resolvedParty.getOriginalKey())
        assertEquals(originalParty.owningKey, resolvedParty.getOwningKey())
        assertEquals(originalParty.name.toString(), resolvedParty.getLegalName())
        assertEquals(originalParty, resolvedParty.originalOrCurrentParty)
        assertEquals(null, resolvedParty.proofChain)
    }

    @Test
    fun `the resolved party contains the current party details and the original party if a proof exists`() {

        val originalPairKey = newKeyPair()
        val newPairKey = newKeyPair()
        val originalParty = newParty("Alice", originalPairKey.public)
        val rotatedParty = newParty("Alice", newPairKey.public)
        val proof = createProof(originalPairKey, newPairKey.public)
        val proofsByOriginalKeyMap = mapOf(originalPairKey.public to KeyRotationProofChain(listOf(proof)))
        val resolver = PartyIdentityResolver(proofsByOriginalKeyMap)
        val resolvedParty = resolver.resolve(originalParty)

        assertTrue(resolvedParty.containsProof())
        assertEquals(originalParty.owningKey, resolvedParty.getOriginalKey())
        assertEquals(rotatedParty.owningKey, resolvedParty.getOwningKey())
        assertEquals(originalParty.name.toString(), resolvedParty.getLegalName())
        assertEquals(rotatedParty, resolvedParty.originalOrCurrentParty)
        assertEquals(rotatedParty.owningKey, resolvedParty.originalOrCurrentParty.owningKey)
    }

    @Test
    fun `isSameParty returns the right value`() {

        val originalPairKey = newKeyPair()
        val newPairKey = newKeyPair()
        val originalParty = newParty("Alice", originalPairKey.public)
        val rotatedParty = newParty("Alice", newPairKey.public)
        val noProofParty = newParty("Alice",  newKeyPair().public)
        val differentParty = newParty("Bob",  newKeyPair().public)
        val proof = createProof(originalPairKey, newPairKey.public)
        val proofsByOriginalKeyMap = mapOf(originalPairKey.public to KeyRotationProofChain(listOf(proof)))
        val resolver = PartyIdentityResolver(proofsByOriginalKeyMap)

        assertTrue(resolver.isSameParty(originalParty, rotatedParty))
        assertTrue(resolver.isSameParty(rotatedParty, originalParty))
        assertTrue(resolver.isSameParty(originalParty, originalParty))
        assertTrue(resolver.isSameParty(rotatedParty, rotatedParty))
        assertFalse(resolver.isSameParty(noProofParty, rotatedParty))
        assertFalse(resolver.isSameParty(rotatedParty, noProofParty))
        assertFalse(resolver.isSameParty(noProofParty, originalParty))
        assertFalse(resolver.isSameParty(originalParty, noProofParty))
        assertFalse(resolver.isSameParty(originalParty, differentParty))
        assertFalse(resolver.isSameParty(differentParty, originalParty))
    }

    @Test
    fun `isSameParty returns false if proof is tampered with`() {

        val originalPairKey = newKeyPair()
        val newPairKey = newKeyPair()
        val tempPairKey = newKeyPair()

        val originalParty = newParty("Alice", originalPairKey.public)
        val rotatedParty = newParty("Alice", newPairKey.public)
        val tamperedProofParty = newParty("Alice", tempPairKey.public)

        val proof = createProof(originalPairKey, newPairKey.public)
        val proof2 = createProof(originalPairKey, tempPairKey.public)
        val tamperedProofSignatureWrong = proof.copy(signature = proof2.signature)
        val tamperedProofNewKeyWrong = proof.copy(publicKeyNew = tempPairKey.public)

        val proofsByOriginalKeyMap = mapOf(
                originalPairKey.public to KeyRotationProofChain(listOf(tamperedProofSignatureWrong)),
                originalPairKey.public to KeyRotationProofChain(listOf(tamperedProofSignatureWrong, proof)),
                originalPairKey.public to KeyRotationProofChain(listOf(proof, tamperedProofSignatureWrong)),
                originalPairKey.public to KeyRotationProofChain(listOf(tamperedProofNewKeyWrong)),
                originalPairKey.public to KeyRotationProofChain(listOf(tamperedProofNewKeyWrong, proof)),
                originalPairKey.public to KeyRotationProofChain(listOf(proof, tamperedProofNewKeyWrong))
        )
        val resolver = PartyIdentityResolver(proofsByOriginalKeyMap)

        assertFalse(resolver.isSameParty(originalParty, rotatedParty))
        assertFalse(resolver.isSameParty(rotatedParty, originalParty))
        assertFalse(resolver.isSameParty(originalParty, tamperedProofParty))
        assertFalse(resolver.isSameParty(tamperedProofParty, originalParty))
    }

    @Test
    fun `isRequiredSigner returns the right value`() {

        val originalPairKey = newKeyPair()
        val newPairKey = newKeyPair()
        val originalParty = newParty("Alice", originalPairKey.public)
        val rotatedParty = newParty("Alice", newPairKey.public)
        val noProofParty = newParty("Alice",  newKeyPair().public)
        val differentParty = newParty("Bob",  newKeyPair().public)

        val signers = listOf(rotatedParty.owningKey)
        val proof = createProof(originalPairKey, newPairKey.public)
        val proofsByOriginalKeyMap = mapOf(originalPairKey.public to KeyRotationProofChain(listOf(proof)))
        val resolver = PartyIdentityResolver(proofsByOriginalKeyMap)

        assertTrue(resolver.isRequiredSigner(signers, rotatedParty))
        assertTrue(resolver.isRequiredSigner(signers, originalParty))
        assertFalse(resolver.isRequiredSigner(signers, noProofParty))
        assertFalse(resolver.isRequiredSigner(signers, differentParty))
    }

    @Test
    fun `isRequiredSigner returns false if proof is tampered with`() {

        val originalPairKey = newKeyPair()
        val newPairKey = newKeyPair()
        val tempPairKey = newKeyPair()

        val originalParty = newParty("Alice", originalPairKey.public)
        val rotatedParty = newParty("Alice", newPairKey.public)
        val tamperedProofParty = newParty("Alice", tempPairKey.public)

        val proof = createProof(originalPairKey, newPairKey.public)
        val proof2 = createProof(originalPairKey, tempPairKey.public)
        val tamperedProofSignatureWrong = proof.copy(signature = proof2.signature)
        val tamperedProofNewKeyWrong = proof.copy(publicKeyNew = tempPairKey.public)

        val proofsByOriginalKeyMap = mapOf(
                originalPairKey.public to KeyRotationProofChain(listOf(tamperedProofSignatureWrong)),
                originalPairKey.public to KeyRotationProofChain(listOf(tamperedProofSignatureWrong, proof)),
                originalPairKey.public to KeyRotationProofChain(listOf(proof, tamperedProofSignatureWrong)),
                originalPairKey.public to KeyRotationProofChain(listOf(tamperedProofNewKeyWrong)),
                originalPairKey.public to KeyRotationProofChain(listOf(tamperedProofNewKeyWrong, proof)),
                originalPairKey.public to KeyRotationProofChain(listOf(proof, tamperedProofNewKeyWrong))
        )
        val resolver = PartyIdentityResolver(proofsByOriginalKeyMap)
        val signers = listOf(rotatedParty.owningKey)

        assertTrue(resolver.isRequiredSigner(signers, rotatedParty))
        assertFalse(resolver.isRequiredSigner(signers, originalParty))
        assertFalse(resolver.isRequiredSigner(signers, tamperedProofParty))
    }

    private fun newParty(commonName: String, publicKey: PublicKey): Party {
        return Party(CordaX500Name(commonName, "London", "GB"), publicKey)
    }

    private fun newKeyPair(): KeyPair = Crypto.generateKeyPair()

    private fun createProof(oldKeyPair: KeyPair, newPublicKey: PublicKey): KeyRotationProof {
        val signature = Crypto.doSign(oldKeyPair.private, newPublicKey.encoded)
        return KeyRotationProof(oldKeyPair.public, newPublicKey, signature)
    }
}
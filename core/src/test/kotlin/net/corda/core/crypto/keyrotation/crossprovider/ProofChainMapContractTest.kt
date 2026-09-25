package net.corda.core.crypto.keyrotation.crossprovider

import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.PublicKeyComparator
import net.corda.core.contracts.newKeyRotationProofChainMap
import net.corda.core.contracts.toSortedMap
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.PublicKey
import java.util.SortedMap
import java.util.TreeMap
import kotlin.test.assertFailsWith

/**
 * Pins the contract between the three places a key-rotation proof-chain map is made or consumed:
 *
 *  - [PartyIdentityResolver.generateProofChainMap]  (produces it for CorDapp authors)
 *  - [toSortedMap]                                  (the one construction site)
 *  - [Command] / [CommandWithParties]               (consume it, and enforce non-empty + [PublicKeyComparator])
 *
 * If any of them drifts - a different comparator, a different return type, an empty map where null is required -
 * one of these tests fails.
 */
class ProofChainMapContractTest {

    private class DummyCommand : CommandData

    @Test(timeout = 300_000)
    fun `generateProofChainMap returns exactly the type Command and CommandWithParties take`() {
        val (resolvedAlice, aliceOld) = rotatedParty("Alice")
        val (resolvedBob, bobOld) = rotatedParty("Bob")

        // The declared return type must BE the declared parameter type. Assigning through an explicitly typed local
        // and then passing it to both constructors makes this a compile-time check, not just a runtime one.
        val map = PartyIdentityResolver.generateProofChainMap(resolvedAlice, resolvedBob)
        assertNotNull(map)

        val command = Command(DummyCommand(), listOf(aliceOld.public, bobOld.public), map)
        val commandWithParties = CommandWithParties(listOf(aliceOld.public, bobOld.public), emptyList(), DummyCommand(), map)

        assertSame(map, command.keyRotationProofChainMap)
        assertSame(map, commandWithParties.keyRotationProofChainMap)

        assertEquals(setOf(aliceOld.public, bobOld.public), map!!.keys)
        assertEquals(resolvedAlice.proofChain, map[aliceOld.public])
        assertEquals(resolvedBob.proofChain, map[bobOld.public])
    }

    @Test(timeout = 300_000)
    fun `generateProofChainMap and toSortedMap build identical maps with the same comparator`() {
        val (resolvedAlice, aliceOld) = rotatedParty("Alice")
        val (resolvedBob, bobOld) = rotatedParty("Bob")
        val (resolvedCarol, carolOld) = rotatedParty("Carol")

        val generated = PartyIdentityResolver.generateProofChainMap(resolvedAlice, resolvedBob, resolvedCarol)!!
        val viaExtension = mapOf(
                aliceOld.public to resolvedAlice.proofChain!!,
                bobOld.public to resolvedBob.proofChain!!,
                carolOld.public to resolvedCarol.proofChain!!
        ).toSortedMap()

        assertSame(PublicKeyComparator, generated.comparator())
        assertSame(PublicKeyComparator, viaExtension.comparator())
        assertTrue(generated.comparator() is PublicKeyComparator)
        assertEquals(viaExtension, generated)
        assertEquals(viaExtension.keys.toList(), generated.keys.toList())
        assertEquals(viaExtension.values.toList(), generated.values.toList())

        // And both are accepted by the consumers.
        Command(DummyCommand(), listOf(aliceOld.public), generated)
        Command(DummyCommand(), listOf(aliceOld.public), viaExtension)
    }

    @Test(timeout = 300_000)
    fun `every producer builds the map from the one factory`() {
        val (resolvedAlice, _) = rotatedParty("Alice")
        val generated = PartyIdentityResolver.generateProofChainMap(resolvedAlice)!!
        val fromFactory = newKeyRotationProofChainMap()
        val fromExtension = emptyMap<PublicKey, KeyRotationProofChain>().toSortedMap()

        assertSame(fromFactory.comparator(), generated.comparator())
        assertSame(fromFactory.comparator(), fromExtension.comparator())
        assertSame(PublicKeyComparator, fromFactory.comparator())
        assertTrue(generated is TreeMap<PublicKey, KeyRotationProofChain>)
    }

    @Test(timeout = 300_000)
    fun `insertion order never matters - the comparator alone decides the order`() {
        val (resolvedAlice, aliceOld) = rotatedParty("Alice")
        val (resolvedBob, bobOld) = rotatedParty("Bob")

        val abOrder = PartyIdentityResolver.generateProofChainMap(resolvedAlice, resolvedBob)!!
        val baOrder = PartyIdentityResolver.generateProofChainMap(resolvedBob, resolvedAlice)!!
        val extension = mapOf(bobOld.public to resolvedBob.proofChain!!, aliceOld.public to resolvedAlice.proofChain!!).toSortedMap()

        assertEquals(abOrder.keys.toList(), baOrder.keys.toList())
        assertEquals(abOrder.keys.toList(), extension.keys.toList())
        assertEquals(listOf(aliceOld.public, bobOld.public).sortedWith(PublicKeyComparator), abOrder.keys.toList())
    }

    @Test(timeout = 300_000)
    fun `generateProofChainMap returns null rather than the empty map that Command rejects`() {
        val notRotated = PartyIdentityResolved(newParty("Alice", newKeyPair().public), null as KeyRotationProofChain?)

        // No proofs -> null, which Command accepts...
        assertNull(PartyIdentityResolver.generateProofChainMap(notRotated))
        assertNull(PartyIdentityResolver.generateProofChainMap())
        Command(DummyCommand(), listOf(newKeyPair().public), null)

        // ...whereas toSortedMap() on an empty map yields an EMPTY map, which Command must reject. That asymmetry is
        // deliberate (TransactionUtils uses empty maps to pad the component group), and generateProofChainMap exists
        // precisely so CorDapp authors never hand Command that empty map.
        val empty = emptyMap<PublicKey, KeyRotationProofChain>().toSortedMap()
        assertFailsWith<IllegalArgumentException> { Command(DummyCommand(), listOf(newKeyPair().public), empty) }
        assertFailsWith<IllegalArgumentException> { CommandWithParties(listOf(newKeyPair().public), emptyList(), DummyCommand(), empty) }
    }

    @Test(timeout = 300_000)
    fun `Command rejects a proof-chain map built with any other comparator`() {
        val (resolvedAlice, aliceOld) = rotatedParty("Alice")
        // A map with the right contents but the wrong comparator - what a hand-rolled construction could produce.
        val wrongComparator = java.util.TreeMap<PublicKey, KeyRotationProofChain>(compareBy { it.hashCode() })
        wrongComparator[aliceOld.public] = resolvedAlice.proofChain!!

        assertFailsWith<IllegalArgumentException> { Command(DummyCommand(), listOf(aliceOld.public), wrongComparator) }
        assertFailsWith<IllegalArgumentException> { CommandWithParties(listOf(aliceOld.public), emptyList(), DummyCommand(), wrongComparator) }
    }

    // ---- helpers ----

    /** A party that has rotated once: returns its resolved form (carrying the proof) and its ORIGINAL key pair. */
    private fun rotatedParty(name: String): Pair<PartyIdentityResolved, KeyPair> {
        val oldKeyPair = newKeyPair()
        val newKeyPair = newKeyPair()
        val proof = KeyRotationProof(oldKeyPair.public, newKeyPair.public, Crypto.doSign(oldKeyPair.private, newKeyPair.public.encoded))
        val resolved = PartyIdentityResolved(newParty(name, oldKeyPair.public), KeyRotationProofChain(listOf(proof)))
        return resolved to oldKeyPair
    }

    private fun newParty(commonName: String, publicKey: PublicKey) = Party(CordaX500Name(commonName, "London", "GB"), publicKey)

    private fun newKeyPair(): KeyPair = Crypto.generateKeyPair()
}

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
class ProofChainMapGenerationTest {

    private class DummyCommand : CommandData

    @Test(timeout = 300_000)
    fun `generateProofChainMap returns exactly the type Command and CommandWithParties take`() {
        val (resolvedAlice, aliceOld) = rotatedParty("Alice")
        val (resolvedBob, bobOld) = rotatedParty("Bob")

        // The declared return type must BE the declared parameter type. Assigning through an explicitly typed local
        // and then passing it to both constructors makes this a compile-time check, not just a runtime one.
        val map: SortedMap<PublicKey, KeyRotationProofChain>? = PartyIdentityResolver.generateProofChainMap(resolvedAlice, resolvedBob)
        assertNotNull(map)

        // Make sure both command and commandWithParties accept the map
        val command = Command(DummyCommand(), listOf(aliceOld.public, bobOld.public), map)
        val commandWithParties = CommandWithParties(listOf(aliceOld.public, bobOld.public), emptyList(), DummyCommand(), map)

        // Make sure the map is the same instance as the one passed to the constructors
        assertSame(map, command.keyRotationProofChainMap)
        assertSame(map, commandWithParties.keyRotationProofChainMap)

        // Ensure the map has the right contents, and that the proof chains are the same as those in the resolved parties.
        assertEquals(setOf(aliceOld.public, bobOld.public), map!!.keys)
        assertEquals(resolvedAlice.proofChain, map[aliceOld.public])
        assertEquals(resolvedBob.proofChain, map[bobOld.public])
    }

    @Test(timeout = 300_000)
    fun `toSortedMap returns exactly the type Command and CommandWithParties take`() {
        val (resolvedAlice, aliceOld) = rotatedParty("Alice")
        val (resolvedBob, bobOld) = rotatedParty("Bob")

        // The declared return type must BE the declared parameter type. Assigning through an explicitly typed local
        // and then passing it to both constructors makes this a compile-time check, not just a runtime one.
        val map: SortedMap<PublicKey, KeyRotationProofChain>? = mapOf(aliceOld.public to resolvedAlice.proofChain!!, bobOld.public to resolvedBob.proofChain!!).toSortedMap()
        assertNotNull(map)

        // Make sure both command and commandWithParties accept the map
        val command = Command(DummyCommand(), listOf(aliceOld.public, bobOld.public), map)
        val commandWithParties = CommandWithParties(listOf(aliceOld.public, bobOld.public), emptyList(), DummyCommand(), map)

        // Make sure the map is the same instance as the one passed to the constructors
        assertSame(map, command.keyRotationProofChainMap)
        assertSame(map, commandWithParties.keyRotationProofChainMap)

        // Ensure the map has the right contents, and that the proof chains are the same as those in the resolved parties.
        assertEquals(setOf(aliceOld.public, bobOld.public), map!!.keys)
        assertEquals(resolvedAlice.proofChain, map[aliceOld.public])
        assertEquals(resolvedBob.proofChain, map[bobOld.public])
    }

    @Test(timeout = 300_000)
    fun `generateProofChainMap and toSortedMap build identical maps with the same comparator`() {
        val (resolvedAlice, aliceOld) = rotatedParty("Alice")
        val (resolvedBob, bobOld) = rotatedParty("Bob")
        val (resolvedCarol, carolOld) = rotatedParty("Carol")

        // Generate map with generateProofChainMap
        val mapFromGenerateProofChainMap = PartyIdentityResolver.generateProofChainMap(resolvedAlice, resolvedBob, resolvedCarol)!!

        // Generate map with toSortedMap extension function
        val mapFromToSortedMap = mapOf(aliceOld.public to resolvedAlice.proofChain!!, bobOld.public to resolvedBob.proofChain!!, carolOld.public to resolvedCarol.proofChain!!).toSortedMap()

        // Check that both maps have the same comparator, and that it is PublicKeyComparator
        assertSame(PublicKeyComparator, mapFromGenerateProofChainMap.comparator())
        assertSame(PublicKeyComparator, mapFromToSortedMap.comparator())
        assertTrue(mapFromGenerateProofChainMap.comparator() is PublicKeyComparator)

        // Check that both maps have the same contents and order
        assertEquals(mapFromToSortedMap, mapFromGenerateProofChainMap)
        assertEquals(mapFromToSortedMap.keys.toList(), mapFromGenerateProofChainMap.keys.toList())
        assertEquals(mapFromToSortedMap.values.toList(), mapFromGenerateProofChainMap.values.toList())
    }

    @Test(timeout = 300_000)
    fun `PublicKeyComparator is used by generateProofChainMap, toSortedMap, and newKeyRotationProofChainMap`() {
        val (resolvedAlice, _) = rotatedParty("Alice")
        val mapFromGenerateProofChainMap = PartyIdentityResolver.generateProofChainMap(resolvedAlice)!!
        val mapFromToSortedMap = emptyMap<PublicKey, KeyRotationProofChain>().toSortedMap()
        val fromFactory = newKeyRotationProofChainMap()

        assertSame(fromFactory.comparator(), mapFromGenerateProofChainMap.comparator())
        assertSame(fromFactory.comparator(), mapFromToSortedMap.comparator())
        assertSame(PublicKeyComparator, fromFactory.comparator())
        assertTrue(mapFromGenerateProofChainMap is TreeMap<PublicKey, KeyRotationProofChain>)
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
    fun `generateProofChainMap returns null rather than the empty map`() {
        val notRotated = PartyIdentityResolved(newParty("Alice", newKeyPair().public), null as KeyRotationProofChain?)

        // Parties without proof chains are ignored by the proof chain map generator and null is returned if it results in an empty map
        assertNull(PartyIdentityResolver.generateProofChainMap(notRotated))
        assertNull(PartyIdentityResolver.generateProofChainMap())

        // Check that the command and CommandWithParties accepts keyRotationProofChainMap that are null.
        // Command and CommandWithParties constructors accept null for the keyRotationProofChainMap parameter, which is the expected behavior when no proof chains are present.
        // This is to ensure backwards compatibility when signing transactions that do not require proof chains.
        Command(DummyCommand(), listOf(newKeyPair().public), null)
        CommandWithParties(listOf(newKeyPair().public), emptyList(), DummyCommand(),null)
    }

    @Test(timeout = 300_000)
    fun `toSortedMap can return an empty map that Command and CommandWithParties rejects`() {
        // toSortedMap() on an empty map yields an EMPTY map, which Command must reject. That asymmetry is
        // deliberate (TransactionUtils uses empty maps to pad the component group), and generateProofChainMap exists
        // precisely so CorDapp authors never hand Command that empty map.
        val empty = emptyMap<PublicKey, KeyRotationProofChain>().toSortedMap()
        assertFailsWith<IllegalArgumentException> { Command(DummyCommand(), listOf(newKeyPair().public), empty) }
        assertFailsWith<IllegalArgumentException> { CommandWithParties(listOf(newKeyPair().public), emptyList(), DummyCommand(), empty) }
    }

    @Test(timeout = 300_000)
    fun `Command rejects a proof-chain map built with any other comparator`() {
        val (resolvedAlice, aliceOld) = rotatedParty("Alice")
        // A map with the right contents but the wrong comparator
        val wrongComparator = TreeMap<PublicKey, KeyRotationProofChain>(compareBy { it.hashCode() })
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

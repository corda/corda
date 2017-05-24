package net.corda.node.services.network

import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.testing.ALICE_PUBKEY
import net.corda.testing.BOB_PUBKEY
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Tests for the in memory identity service.
 */
class InMemoryIdentityServiceTests {
    @Test
    fun `get all identities`() {
        val service = InMemoryIdentityService()
        assertNull(service.getAllIdentities().firstOrNull())
        service.registerIdentity(ALICE)
        var expected = setOf(ALICE)
        var actual = service.getAllIdentities().toHashSet()
        assertEquals(expected, actual)

        // Add a second party and check we get both back
        service.registerIdentity(BOB)
        expected = setOf(ALICE, BOB)
        actual = service.getAllIdentities().toHashSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `get identity by key`() {
        val service = InMemoryIdentityService()
        assertNull(service.partyFromKey(ALICE_PUBKEY))
        service.registerIdentity(ALICE)
        assertEquals(ALICE, service.partyFromKey(ALICE_PUBKEY))
        assertNull(service.partyFromKey(BOB_PUBKEY))
    }

    @Test
    fun `get identity by name with no registered identities`() {
        val service = InMemoryIdentityService()
        assertNull(service.partyFromX500Name(ALICE.name))
    }

    @Test
    fun `get identity by name`() {
        val service = InMemoryIdentityService()
        val identities = listOf("Node A", "Node B", "Node C")
                .map { Party(X500Name("CN=$it,O=R3,OU=corda,L=London,C=UK"), generateKeyPair().public) }
        assertNull(service.partyFromX500Name(identities.first().name))
        identities.forEach { service.registerIdentity(it) }
        identities.forEach { assertEquals(it, service.partyFromX500Name(it.name)) }
    }

    /**
     * Generate a certificate path from a root CA, down to a transaction key, store and verify the association.
     */
    @Test
    fun `assert unknown anonymous key is unrecognised`() {
        val rootCertAndKey = X509Utilities.createSelfSignedCACert(ALICE.name)
        val txCertAndKey = X509Utilities.createIntermediateCACert(ALICE.name, rootCertAndKey)
        val service = InMemoryIdentityService()
        val rootKey = rootCertAndKey.keyPair
        // TODO: Generate certificate with an EdDSA key rather than ECDSA
        val identity = Party(rootCertAndKey)
        val txIdentity = AnonymousParty(txCertAndKey.keyPair.public)

        assertFailsWith<IdentityService.UnknownAnonymousPartyException> {
            service.assertOwnership(identity, txIdentity)
        }
    }

    /**
     * Generate a pair of certificate paths from a root CA, down to a transaction key, store and verify the associations.
     * Also checks that incorrect associations are rejected.
     */
    @Test
    fun `assert ownership`() {
        val aliceRootCertAndKey = X509Utilities.createSelfSignedCACert(ALICE.name)
        val aliceTxCertAndKey = X509Utilities.createIntermediateCACert(ALICE.name, aliceRootCertAndKey)
        val aliceCertPath = X509Utilities.createCertificatePath(aliceRootCertAndKey, aliceTxCertAndKey.certificate, false).certPath
        val bobRootCertAndKey = X509Utilities.createSelfSignedCACert(BOB.name)
        val bobTxCertAndKey = X509Utilities.createIntermediateCACert(BOB.name, bobRootCertAndKey)
        val bobCertPath = X509Utilities.createCertificatePath(bobRootCertAndKey, bobTxCertAndKey.certificate, false).certPath
        val service = InMemoryIdentityService()
        val alice = Party(aliceRootCertAndKey)
        val anonymousAlice = AnonymousParty(aliceTxCertAndKey.keyPair.public)
        val bob = Party(bobRootCertAndKey)
        val anonymousBob = AnonymousParty(bobTxCertAndKey.keyPair.public)

        service.registerPath(aliceRootCertAndKey.certificate, anonymousAlice, aliceCertPath)
        service.registerPath(bobRootCertAndKey.certificate, anonymousBob, bobCertPath)

        // Verify that paths are verified
        service.assertOwnership(alice, anonymousAlice)
        service.assertOwnership(bob, anonymousBob)
        assertFailsWith<IllegalArgumentException> {
            service.assertOwnership(alice, anonymousBob)
        }
        assertFailsWith<IllegalArgumentException> {
            service.assertOwnership(bob, anonymousAlice)
        }
    }
}

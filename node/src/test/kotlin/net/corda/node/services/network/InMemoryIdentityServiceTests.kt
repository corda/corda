package net.corda.node.services.network

import net.corda.core.crypto.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.*
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.testing.ALICE_PUBKEY
import net.corda.testing.BOB_PUBKEY
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Tests for the in memory identity service.
 */
class InMemoryIdentityServiceTests {
    @Test
    fun `get all identities`() {
        val service = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        // Nothing registered, so empty set
        assertNull(service.getAllIdentities().firstOrNull())

        service.registerIdentity(ALICE_IDENTITY)
        var expected = setOf<Party>(ALICE)
        var actual = service.getAllIdentities().map { it.party }.toHashSet()
        assertEquals(expected, actual)

        // Add a second party and check we get both back
        service.registerIdentity(BOB_IDENTITY)
        expected = setOf<Party>(ALICE, BOB)
        actual = service.getAllIdentities().map { it.party }.toHashSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `get identity by key`() {
        val service = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        assertNull(service.partyFromKey(ALICE_PUBKEY))
        service.registerIdentity(ALICE_IDENTITY)
        assertEquals(ALICE, service.partyFromKey(ALICE_PUBKEY))
        assertNull(service.partyFromKey(BOB_PUBKEY))
    }

    @Test
    fun `get identity by name with no registered identities`() {
        val service = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        assertNull(service.partyFromX500Name(ALICE.name))
    }

    @Test
    fun `get identity by name`() {
        val service = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        val identities = listOf("Node A", "Node B", "Node C")
                .map { getTestPartyAndCertificate(X500Name("CN=$it,O=R3,OU=corda,L=London,C=UK"), generateKeyPair().public) }
        assertNull(service.partyFromX500Name(identities.first().name))
        identities.forEach { service.registerIdentity(it) }
        identities.forEach { assertEquals(it.party, service.partyFromX500Name(it.name)) }
    }

    /**
     * Generate a certificate path from a root CA, down to a transaction key, store and verify the association.
     */
    @Test
    fun `assert unknown anonymous key is unrecognised`() {
        val rootKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCert = X509Utilities.createSelfSignedCACertificate(ALICE.name, rootKey)
        val txKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val service = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        // TODO: Generate certificate with an EdDSA key rather than ECDSA
        val identity = Party(CertificateAndKeyPair(rootCert, rootKey))
        val txIdentity = AnonymousParty(txKey.public)

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
        val aliceRootKey = Crypto.generateKeyPair()
        val aliceRootCert = X509Utilities.createSelfSignedCACertificate(ALICE.name, aliceRootKey)
        val aliceTxKey = Crypto.generateKeyPair()
        val aliceTxCert = X509Utilities.createCertificate(CertificateType.IDENTITY, aliceRootCert, aliceRootKey, ALICE.name, aliceTxKey.public)
        val aliceCertPath = X509Utilities.createCertificatePath(aliceRootCert, aliceTxCert, revocationEnabled = false)
        val alice = PartyAndCertificate(ALICE.name, aliceRootKey.public, aliceRootCert, aliceCertPath)

        val bobRootKey = Crypto.generateKeyPair()
        val bobRootCert = X509Utilities.createSelfSignedCACertificate(BOB.name, bobRootKey)
        val bobTxKey = Crypto.generateKeyPair()
        val bobTxCert = X509Utilities.createCertificate(CertificateType.IDENTITY, bobRootCert, bobRootKey, BOB.name, bobTxKey.public)
        val bobCertPath = X509Utilities.createCertificatePath(bobRootCert, bobTxCert, revocationEnabled = false)
        val bob = PartyAndCertificate(BOB.name, bobRootKey.public, bobRootCert, bobCertPath)

        // Now we have identities, construct the service and let it know about both
        val service = InMemoryIdentityService(setOf(alice, bob), emptyMap(), null as X509Certificate?)
        val anonymousAlice = AnonymousParty(aliceTxKey.public)
        service.registerAnonymousIdentity(anonymousAlice, alice.party, aliceCertPath)

        val anonymousBob = AnonymousParty(bobTxKey.public)
        service.registerAnonymousIdentity(anonymousBob, bob.party, bobCertPath)

        // Verify that paths are verified
        service.assertOwnership(alice.party, anonymousAlice)
        service.assertOwnership(bob.party, anonymousBob)
        assertFailsWith<IllegalArgumentException> {
            service.assertOwnership(alice.party, anonymousBob)
        }
        assertFailsWith<IllegalArgumentException> {
            service.assertOwnership(bob.party, anonymousAlice)
        }
    }

    /**
     * Ensure if we feed in a full identity, we get the same identity back.
     */
    @Test
    fun `deanonymising a well known identity`() {
        val expected = ALICE
        val actual = InMemoryIdentityService(trustRoot = null).partyFromAnonymous(expected)
        assertEquals(expected, actual)
    }
}

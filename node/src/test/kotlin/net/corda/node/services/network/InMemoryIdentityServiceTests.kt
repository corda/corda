package net.corda.node.services.network

import net.corda.core.crypto.*
import net.corda.core.identity.VerifiedAnonymousParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.VerifiedParty
import net.corda.core.node.services.IdentityService
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.testing.*
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.security.cert.CertificateFactory
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
        var expected = setOf(ALICE)
        var actual = service.getAllIdentities().map { it.party }.toHashSet()
        assertEquals(expected, actual)

        // Add a second party and check we get both back
        service.registerIdentity(BOB_IDENTITY)
        expected = setOf(ALICE, BOB)
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
    fun `get identity by substring match`() {
        val trustRoot = DUMMY_CA
        val service = InMemoryIdentityService(trustRoot = trustRoot.certificate)
        service.registerIdentity(ALICE_IDENTITY)
        service.registerIdentity(BOB_IDENTITY)
        val alicente = getTestVerifedParty(X500Name("O=Alicente Worldwide,L=London,C=GB"), generateKeyPair().public)
        service.registerIdentity(alicente)
        assertEquals(setOf(ALICE, alicente.party), service.partiesFromName("Alice", false))
        assertEquals(setOf(ALICE), service.partiesFromName("Alice Corp", true))
        assertEquals(setOf(BOB), service.partiesFromName("Bob Plc", true))
    }

    @Test
    fun `get identity by name`() {
        val service = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        val identities = listOf("Node A", "Node B", "Node C")
                .map { getTestVerifedParty(X500Name("CN=$it,O=R3,OU=corda,L=London,C=GB"), generateKeyPair().public) }
        assertNull(service.partyFromX500Name(identities.first().name))
        identities.forEach { service.registerIdentity(it) }
        identities.forEach { assertEquals(it.party, service.partyFromX500Name(it.name)) }
    }

    /**
     * Generate a certificate path from a root CA, down to a transaction key, store and verify the association.
     */
    @Test
    fun `assert unknown anonymous key is unrecognised`() {
        withTestSerialization {
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
    }

    /**
     * Generate a pair of certificate paths from a root CA, down to a transaction key, store and verify the associations.
     * Also checks that incorrect associations are rejected.
     */
    @Test
    fun `get anonymous identity by key`() {
        val trustRoot = DUMMY_CA
        val (alice, aliceTxIdentity) = createParty(ALICE.name, trustRoot)
        val (bob, bobTxIdentity) = createParty(ALICE.name, trustRoot)

        // Now we have identities, construct the service and let it know about both
        val service = InMemoryIdentityService(setOf(alice), emptyMap(), trustRoot.certificate.cert)
        service.verifyAndRegisterAnonymousIdentity(aliceTxIdentity, alice.party)

        var actual = service.anonymousFromKey(aliceTxIdentity.party.owningKey)
        assertEquals<VerifiedAnonymousParty>(aliceTxIdentity, actual!!)

        assertNull(service.anonymousFromKey(bobTxIdentity.party.owningKey))
        service.verifyAndRegisterAnonymousIdentity(bobTxIdentity, bob.party)
        actual = service.anonymousFromKey(bobTxIdentity.party.owningKey)
        assertEquals<VerifiedAnonymousParty>(bobTxIdentity, actual!!)
    }

    /**
     * Generate a pair of certificate paths from a root CA, down to a transaction key, store and verify the associations.
     * Also checks that incorrect associations are rejected.
     */
    @Test
    fun `assert ownership`() {
        withTestSerialization {
            val trustRoot = DUMMY_CA
            val (alice, aliceTxIdentity) = createParty(ALICE.name, trustRoot)

            val certFactory = CertificateFactory.getInstance("X509")
            val bobRootKey = Crypto.generateKeyPair()
            val bobRoot = getTestVerifedParty(BOB.name, bobRootKey.public)
            val bobRootCert = bobRoot.certificate
            val bobTxKey = Crypto.generateKeyPair()
            val bobTxCert = X509Utilities.createCertificate(CertificateType.IDENTITY, bobRootCert, bobRootKey, BOB.name, bobTxKey.public)
            val bob = VerifiedParty(BOB.name, bobRootKey.public, bobRootCert, certFactory.generateCertPath(listOf(bobRootCert.cert)))

            // Now we have identities, construct the service and let it know about both
            val service = InMemoryIdentityService(setOf(alice, bob), emptyMap(), trustRoot.certificate.cert)
            service.verifyAndRegisterAnonymousIdentity(aliceTxIdentity, alice.party)

            val bobTxCertPath = certFactory.generateCertPath(listOf(bobTxCert.cert, bobRootCert.cert))
            val anonymousBob = VerifiedAnonymousParty(AnonymousParty(bobTxKey.public), bobTxCertPath)
            service.verifyAndRegisterAnonymousIdentity(anonymousBob, bob.party)

            // Verify that paths are verified
            service.assertOwnership(alice.party, aliceTxIdentity.party)
            service.assertOwnership(bob.party, anonymousBob.party)
            assertFailsWith<IllegalArgumentException> {
                service.assertOwnership(alice.party, anonymousBob.party)
            }
            assertFailsWith<IllegalArgumentException> {
                service.assertOwnership(bob.party, aliceTxIdentity.party)
            }

            assertFailsWith<IllegalArgumentException> {
                val owningKey = Crypto.decodePublicKey(trustRoot.certificate.subjectPublicKeyInfo.encoded)
                service.assertOwnership(Party(trustRoot.certificate.subject, owningKey), aliceTxIdentity.party)
            }
        }
    }

    private fun createParty(x500Name: X500Name, ca: CertificateAndKeyPair): Pair<VerifiedParty, VerifiedAnonymousParty> {
        val certFactory = CertificateFactory.getInstance("X509")
        val issuerKeyPair = generateKeyPair()
        val issuer = getTestVerifedParty(x500Name, issuerKeyPair.public, ca)
        val txKey = Crypto.generateKeyPair()
        val txCert = X509Utilities.createCertificate(CertificateType.IDENTITY, issuer.certificate, issuerKeyPair, x500Name, txKey.public)
        val txCertPath = certFactory.generateCertPath(listOf(txCert.cert) + issuer.certPath.certificates)
        return Pair(issuer, VerifiedAnonymousParty(AnonymousParty(txKey.public), txCertPath))
    }

    /**
     * Ensure if we feed in a full identity, we get the same identity back.
     */
    @Test
    fun `deanonymising a well known identity`() {
        val expected = ALICE
        val actual = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate).partyFromAnonymous(expected)
        assertEquals(expected, actual)
    }
}

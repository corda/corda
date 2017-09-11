package net.corda.node.services.network

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.node.utilities.CertificateAndKeyPair
import net.corda.core.utilities.cert
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.X509Utilities
import net.corda.testing.*
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

        service.verifyAndRegisterIdentity(ALICE_IDENTITY)
        var expected = setOf(ALICE)
        var actual = service.getAllIdentities().map { it.party }.toHashSet()
        assertEquals(expected, actual)

        // Add a second party and check we get both back
        service.verifyAndRegisterIdentity(BOB_IDENTITY)
        expected = setOf(ALICE, BOB)
        actual = service.getAllIdentities().map { it.party }.toHashSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `get identity by key`() {
        val service = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        assertNull(service.partyFromKey(ALICE_PUBKEY))
        service.verifyAndRegisterIdentity(ALICE_IDENTITY)
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
        service.verifyAndRegisterIdentity(ALICE_IDENTITY)
        service.verifyAndRegisterIdentity(BOB_IDENTITY)
        val alicente = getTestPartyAndCertificate(CordaX500Name(organisation = "Alicente Worldwide", locality = "London", country = "GB"), generateKeyPair().public)
        service.verifyAndRegisterIdentity(alicente)
        assertEquals(setOf(ALICE, alicente.party), service.partiesFromName("Alice", false))
        assertEquals(setOf(ALICE), service.partiesFromName("Alice Corp", true))
        assertEquals(setOf(BOB), service.partiesFromName("Bob Plc", true))
    }

    @Test
    fun `get identity by name`() {
        val service = InMemoryIdentityService(trustRoot = DUMMY_CA.certificate)
        val identities = listOf("Org A", "Org B", "Org C")
                .map { getTestPartyAndCertificate(CordaX500Name(organisation = it, locality = "London", country = "GB"), generateKeyPair().public) }
        assertNull(service.partyFromX500Name(identities.first().name))
        identities.forEach { service.verifyAndRegisterIdentity(it) }
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
            val identity = Party(rootCert)
            val txIdentity = AnonymousParty(txKey.public)

            assertFailsWith<UnknownAnonymousPartyException> {
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
        val (_, bobTxIdentity) = createParty(ALICE.name, trustRoot)

        // Now we have identities, construct the service and let it know about both
        val service = InMemoryIdentityService(setOf(alice), emptySet(), trustRoot.certificate.cert)
        service.verifyAndRegisterIdentity(aliceTxIdentity)

        var actual = service.certificateFromKey(aliceTxIdentity.party.owningKey)
        assertEquals(aliceTxIdentity, actual!!)

        assertNull(service.certificateFromKey(bobTxIdentity.party.owningKey))
        service.verifyAndRegisterIdentity(bobTxIdentity)
        actual = service.certificateFromKey(bobTxIdentity.party.owningKey)
        assertEquals(bobTxIdentity, actual!!)
    }

    /**
     * Generate a pair of certificate paths from a root CA, down to a transaction key, store and verify the associations.
     * Also checks that incorrect associations are rejected.
     */
    @Test
    fun `assert ownership`() {
        withTestSerialization {
            val trustRoot = DUMMY_CA
            val (alice, anonymousAlice) = createParty(ALICE.name, trustRoot)
            val (bob, anonymousBob) = createParty(BOB.name, trustRoot)

            // Now we have identities, construct the service and let it know about both
            val service = InMemoryIdentityService(setOf(alice, bob), emptySet(), trustRoot.certificate.cert)

            service.verifyAndRegisterIdentity(anonymousAlice)
            service.verifyAndRegisterIdentity(anonymousBob)

            // Verify that paths are verified
            service.assertOwnership(alice.party, anonymousAlice.party.anonymise())
            service.assertOwnership(bob.party, anonymousBob.party.anonymise())
            assertFailsWith<IllegalArgumentException> {
                service.assertOwnership(alice.party, anonymousBob.party.anonymise())
            }
            assertFailsWith<IllegalArgumentException> {
                service.assertOwnership(bob.party, anonymousAlice.party.anonymise())
            }

            assertFailsWith<IllegalArgumentException> {
                val owningKey = Crypto.decodePublicKey(trustRoot.certificate.subjectPublicKeyInfo.encoded)
                val subject = CordaX500Name.build(trustRoot.certificate.subject)
                service.assertOwnership(Party(subject, owningKey), anonymousAlice.party.anonymise())
            }
        }
    }

    private fun createParty(x500Name: CordaX500Name, ca: CertificateAndKeyPair): Pair<PartyAndCertificate, PartyAndCertificate> {
        val certFactory = CertificateFactory.getInstance("X509")
        val issuerKeyPair = generateKeyPair()
        val issuer = getTestPartyAndCertificate(x500Name, issuerKeyPair.public, ca)
        val txKey = Crypto.generateKeyPair()
        val txCert = X509Utilities.createCertificate(CertificateType.IDENTITY, issuer.certificate, issuerKeyPair, x500Name, txKey.public)
        val txCertPath = certFactory.generateCertPath(listOf(txCert.cert) + issuer.certPath.certificates)
        return Pair(issuer, PartyAndCertificate(txCertPath))
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

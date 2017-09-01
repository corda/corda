package net.corda.node.services.network

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.core.utilities.CertificateAndKeyPair
import net.corda.core.utilities.cert
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.utilities.CertificateType
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.X509Utilities
import net.corda.testing.*
import net.corda.testing.node.MockServices
import org.bouncycastle.asn1.x500.X500Name
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.cert.CertificateFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Tests for the in memory identity service.
 */
class PersistentIdentityServiceTests {

    lateinit var database: CordaPersistence
    lateinit var services: MockServices
    lateinit var identityService: IdentityService

    @Before
    fun setup() {
        val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(keys = emptyList(), createIdentityService = { PersistentIdentityService(trustRoot = DUMMY_CA.certificate) })
        database = databaseAndServices.first
        services = databaseAndServices.second
        identityService = services.identityService
    }

    @After
    fun shutdown() {
        database.close()
    }

    @Test
    fun `get all identities`() {
        // Nothing registered, so empty set
        database.transaction {
            assertNull(identityService.getAllIdentities().firstOrNull())
        }

        database.transaction {
            identityService.verifyAndRegisterIdentity(ALICE_IDENTITY)
        }
        var expected = setOf(ALICE)
        var actual = database.transaction {
            identityService.getAllIdentities().map { it.party }.toHashSet()
        }
        assertEquals(expected, actual)

        // Add a second party and check we get both back
        database.transaction {
            identityService.verifyAndRegisterIdentity(BOB_IDENTITY)
        }
        expected = setOf(ALICE, BOB)
        actual = database.transaction {
            identityService.getAllIdentities().map { it.party }.toHashSet()
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `get identity by key`() {
        database.transaction {
            assertNull(identityService.partyFromKey(ALICE_PUBKEY))
            identityService.verifyAndRegisterIdentity(ALICE_IDENTITY)
            assertEquals(ALICE, identityService.partyFromKey(ALICE_PUBKEY))
            assertNull(identityService.partyFromKey(BOB_PUBKEY))
        }
    }

    @Test
    fun `get identity by name with no registered identities`() {
        database.transaction {
            assertNull(identityService.partyFromX500Name(ALICE.name))
        }
    }

    @Test
    fun `get identity by substring match`() {
        database.transaction {
            identityService.verifyAndRegisterIdentity(ALICE_IDENTITY)
            identityService.verifyAndRegisterIdentity(BOB_IDENTITY)
        }
        val alicente = getTestPartyAndCertificate(X500Name("O=Alicente Worldwide,L=London,C=GB"), generateKeyPair().public)
        database.transaction {
            identityService.verifyAndRegisterIdentity(alicente)
            assertEquals(setOf(ALICE, alicente.party), identityService.partiesFromName("Alice", false))
            assertEquals(setOf(ALICE), identityService.partiesFromName("Alice Corp", true))
            assertEquals(setOf(BOB), identityService.partiesFromName("Bob Plc", true))
        }
    }

    @Test
    fun `get identity by name`() {
        val identities = listOf("Node A", "Node B", "Node C")
                .map { getTestPartyAndCertificate(X500Name("CN=$it,O=R3,OU=corda,L=London,C=GB"), generateKeyPair().public) }
        database.transaction {
            assertNull(identityService.partyFromX500Name(identities.first().name))
        }
        identities.forEach {
            database.transaction {
                identityService.verifyAndRegisterIdentity(it)
            }
        }
        identities.forEach {
            database.transaction {
                assertEquals(it.party, identityService.partyFromX500Name(it.name))
            }
        }
    }

    /**
     * Generate a certificate path from a root CA, down to a transaction key, store and verify the association.
     */
    @Test
    fun `assert unknown anonymous key is unrecognised`() {
        withTestSerialization {
            val rootKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val rootCert = X509Utilities.createSelfSignedCACertificate(ALICE.name, rootKey)
            val txKey = Crypto.generateKeyPair(X509Utilities.DEFAULT_IDENTITY_SIGNATURE_SCHEME)
            val identity = Party(rootCert)
            val txIdentity = AnonymousParty(txKey.public)

            assertFailsWith<UnknownAnonymousPartyException> {
                database.transaction {
                    identityService.assertOwnership(identity, txIdentity)
                }
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
        database.transaction {
            identityService.verifyAndRegisterIdentity(alice)
            identityService.verifyAndRegisterIdentity(aliceTxIdentity)
        }

        var actual = database.transaction {
            identityService.certificateFromKey(aliceTxIdentity.party.owningKey)
        }
        assertEquals(aliceTxIdentity, actual!!)

        database.transaction {
            assertNull(identityService.certificateFromKey(bobTxIdentity.party.owningKey))
        }
        database.transaction {
            identityService.verifyAndRegisterIdentity(bobTxIdentity)
        }
        actual = database.transaction {
            identityService.certificateFromKey(bobTxIdentity.party.owningKey)
        }
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

            database.transaction {
                // Now we have identities, construct the service and let it know about both
                identityService.verifyAndRegisterIdentity(anonymousAlice)
                identityService.verifyAndRegisterIdentity(anonymousBob)
            }

            // Verify that paths are verified
            database.transaction {
                identityService.assertOwnership(alice.party, anonymousAlice.party.anonymise())
                identityService.assertOwnership(bob.party, anonymousBob.party.anonymise())
            }
            assertFailsWith<IllegalArgumentException> {
                database.transaction {
                    identityService.assertOwnership(alice.party, anonymousBob.party.anonymise())
                }
            }
            assertFailsWith<IllegalArgumentException> {
                database.transaction {
                    identityService.assertOwnership(bob.party, anonymousAlice.party.anonymise())
                }
            }

            assertFailsWith<IllegalArgumentException> {
                val owningKey = Crypto.decodePublicKey(trustRoot.certificate.subjectPublicKeyInfo.encoded)
                database.transaction {
                    identityService.assertOwnership(Party(trustRoot.certificate.subject, owningKey), anonymousAlice.party.anonymise())
                }
            }
        }
    }

    @Test
    fun `Test Persistence`() {
        val trustRoot = DUMMY_CA
        val (alice, anonymousAlice) = createParty(ALICE.name, trustRoot)
        val (bob, anonymousBob) = createParty(BOB.name, trustRoot)

        database.transaction {
            // Register well known identities
            identityService.verifyAndRegisterIdentity(alice)
            identityService.verifyAndRegisterIdentity(bob)
            // Register an anonymous identities
            identityService.verifyAndRegisterIdentity(anonymousAlice)
            identityService.verifyAndRegisterIdentity(anonymousBob)
        }

        // Create new identity service mounted onto same DB
        val newPersistentIdentityService = database.transaction {
            PersistentIdentityService(trustRoot = DUMMY_CA.certificate)
        }

        database.transaction {
            newPersistentIdentityService.assertOwnership(alice.party, anonymousAlice.party.anonymise())
            newPersistentIdentityService.assertOwnership(bob.party, anonymousBob.party.anonymise())
        }

        val aliceParent = database.transaction {
            newPersistentIdentityService.partyFromAnonymous(anonymousAlice.party.anonymise())
        }
        assertEquals(alice.party, aliceParent!!)

        val bobReload = database.transaction {
            newPersistentIdentityService.certificateFromKey(anonymousBob.party.owningKey)
        }
        assertEquals(anonymousBob, bobReload!!)
    }

    private fun createParty(x500Name: X500Name, ca: CertificateAndKeyPair): Pair<PartyAndCertificate, PartyAndCertificate> {
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
        val actual = database.transaction {
            identityService.partyFromAnonymous(expected)
        }
        assertEquals(expected, actual)
    }
}

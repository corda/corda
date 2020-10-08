package net.corda.node.migration

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.coretesting.internal.rigorousMock
import net.corda.node.services.api.SchemaService
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class IdenityServiceKeyRotationTest {
    private lateinit var liquibaseDB: Database
    private lateinit var cordaDB: CordaPersistence

    @Before
    fun setUp() {
        val schemaService = rigorousMock<SchemaService>()
        doReturn(setOf(IdentityTestSchemaV1, KMSTestSchemaV1)).whenever(schemaService).schemas

        cordaDB = configureDatabase(
                MockServices.makeTestDataSourceProperties(),
                DatabaseConfig(),
                { null },
                { null },
                schemaService = schemaService,
                internalSchemas = setOf(),
                ourName = ALICE_NAME)
        liquibaseDB = H2Database()
        liquibaseDB.connection = JdbcConnection(cordaDB.dataSource.connection)
        liquibaseDB.isAutoCommit = true
    }

    @After
    fun close() {
        contextTransactionOrNull?.close()
        cordaDB.close()
        liquibaseDB.close()
    }

    private fun persist(vararg entries: Any) = cordaDB.transaction {
        entries.forEach { session.persist(it) }
    }

    private fun PartyAndCertificate.dbCertificate() = IdentityTestSchemaV1.NodeIdentities(owningKey.toStringShort(), certPath.encoded)

    private fun Party.dbParty() = IdentityTestSchemaV1.NodeIdentitiesNoCert(owningKey.toStringShort(), name.toString())

    private fun TestIdentity.dbName() = IdentityTestSchemaV1.NodeNamedIdentities(name.toString(), publicKey.toStringShort())

    private fun TestIdentity.dbKey() = IdentityTestSchemaV1.NodeHashToKey(publicKey.toStringShort(), publicKey.encoded)

    private fun TestIdentity.dbKeyPair() =
            KMSTestSchemaV1.PersistentKey(publicKey.toStringShort(), publicKey.encoded, keyPair.private.encoded)

    private fun TestIdentity.generateConfidentialIdentityWithCert(): PartyAndCertificate {
        val certificate = X509Utilities.createCertificate(
                CertificateType.CONFIDENTIAL_LEGAL_IDENTITY,
                identity.certificate,
                keyPair,
                name.x500Principal,
                Crypto.generateKeyPair().public)
        return PartyAndCertificate(X509Utilities.buildCertPath(certificate, identity.certPath.x509Certificates))
    }

    @Test(timeout = 300_000)
    fun `test migration`() {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bob = TestIdentity(BOB_NAME, 80)
        val charlie = TestIdentity(CHARLIE_NAME, 90)

        val alice2 = TestIdentity(ALICE_NAME, 71)
        val alice3 = TestIdentity(ALICE_NAME, 72)
        val aliceCiWithCert = alice.generateConfidentialIdentityWithCert()

        val bob2 = TestIdentity(BOB_NAME, 81)
        val bob3 = TestIdentity(BOB_NAME, 82)

        val charlie2 = TestIdentity(CHARLIE_NAME, 91)
        val charlie3 = TestIdentity(CHARLIE_NAME, 92)
        val charlieCiWithCert = charlie.generateConfidentialIdentityWithCert()

        persist(alice.identity.dbCertificate(), alice.party.dbParty(), alice.dbName())
        persist(charlie.identity.dbCertificate(), charlie.party.dbParty())
        persist(bob.identity.dbCertificate(), bob.party.dbParty(), bob.dbName())

        persist(alice2.party.dbParty(), alice2.dbKeyPair())
        persist(alice3.party.dbParty())
        persist(aliceCiWithCert.dbCertificate(), aliceCiWithCert.party.dbParty())

        persist(bob2.party.dbParty(), bob2.dbKey())
        persist(bob3.party.dbParty())

        persist(charlie2.party.dbParty(), charlie2.dbKey())
        persist(charlie3.party.dbParty())
        persist(charlieCiWithCert.dbCertificate(), charlieCiWithCert.party.dbParty())

        Liquibase("migration/node-core.changelog-v20.xml", object : ClassLoaderResourceAccessor() {
            override fun getResourcesAsStream(path: String) = super.getResourcesAsStream(path)?.firstOrNull()?.let { setOf(it) }
        }, liquibaseDB).update(Contexts().toString())

        val dummyKey = Crypto.generateKeyPair().public
        val results = mutableMapOf<String, List<*>>()

        cordaDB.transaction {
            connection.prepareStatement("SELECT pk_hash, name, party_pk_hash, public_key FROM node_identities_no_cert").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val partyKeyHash = rs.getString(3).takeIf { it.isNotEmpty() } ?: dummyKey.toStringShort()
                        val key = if (rs.getBytes(4).isNotEmpty()) Crypto.decodePublicKey(rs.getBytes(4)) else dummyKey
                        results[rs.getString(1)] = listOf(key, CordaX500Name.parse(rs.getString(2)), partyKeyHash)
                    }
                }
            }
        }

        assertEquals(11, results.size)

        listOf(alice.identity, bob.identity, charlie.identity, aliceCiWithCert, charlieCiWithCert).forEach {
            assertEquals(results[it.owningKey.toStringShort()], listOf(it.owningKey, it.party.name, it.owningKey.toStringShort()))
        }

        assertEquals(results[alice2.publicKey.toStringShort()], listOf(alice2.publicKey, ALICE_NAME, alice.publicKey.toStringShort()))
        assertEquals(results[alice3.publicKey.toStringShort()], listOf(dummyKey, ALICE_NAME, alice.publicKey.toStringShort()))

        assertEquals(results[bob2.publicKey.toStringShort()], listOf(bob2.publicKey, BOB_NAME, bob.publicKey.toStringShort()))
        assertEquals(results[bob3.publicKey.toStringShort()], listOf(dummyKey, BOB_NAME, bob.publicKey.toStringShort()))

        assertEquals(results[charlie2.publicKey.toStringShort()], listOf(charlie2.publicKey, CHARLIE_NAME, dummyKey.toStringShort()))
        assertEquals(results[charlie3.publicKey.toStringShort()], listOf(dummyKey, CHARLIE_NAME, dummyKey.toStringShort()))
    }
}
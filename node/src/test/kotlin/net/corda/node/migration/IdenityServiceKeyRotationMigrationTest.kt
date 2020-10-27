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
import net.corda.coretesting.internal.rigorousMock
import net.corda.node.services.api.SchemaService
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

class IdenityServiceKeyRotationMigrationTest {
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

    private fun Party.dbParty() = IdentityTestSchemaV1.NodeIdentitiesNoCert(owningKey.toStringShort(), name.toString())

    private fun TestIdentity.dbName() = IdentityTestSchemaV1.NodeNamedIdentities(name.toString(), publicKey.toStringShort())

    @Test(timeout = 300_000)
    fun `test migration`() {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bob = TestIdentity(BOB_NAME, 80)
        val charlie = TestIdentity(CHARLIE_NAME, 90)

        val alice2 = TestIdentity(ALICE_NAME, 71)
        val bob2 = TestIdentity(BOB_NAME, 81)
        val charlie2 = TestIdentity(CHARLIE_NAME, 91)

        persist(alice.party.dbParty(), alice.dbName())
        persist(charlie.party.dbParty())
        persist(bob.party.dbParty(), bob.dbName())

        persist(alice2.party.dbParty())
        persist(bob2.party.dbParty())
        persist(charlie2.party.dbParty())

        Liquibase("migration/node-core.changelog-v20.xml", object : ClassLoaderResourceAccessor() {
            override fun getResourcesAsStream(path: String) = super.getResourcesAsStream(path)?.firstOrNull()?.let { setOf(it) }
        }, liquibaseDB).update(Contexts().toString())

        val dummyKey = Crypto.generateKeyPair().public
        val results = mutableMapOf<String, Pair<CordaX500Name, String>>()

        cordaDB.transaction {
            connection.prepareStatement("SELECT pk_hash, name, owning_pk_hash FROM node_identities_no_cert").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val partyKeyHash = rs.getString(3).takeUnless { it == "unresolved" } ?: dummyKey.toStringShort()
                        results[rs.getString(1)] = CordaX500Name.parse(rs.getString(2)) to partyKeyHash
                    }
                }
            }
        }

        assertEquals(6, results.size)

        assertEquals(results[alice.publicKey.toStringShort()], ALICE_NAME to alice.publicKey.toStringShort())
        assertEquals(results[bob.publicKey.toStringShort()], BOB_NAME to bob.publicKey.toStringShort())
        assertEquals(results[charlie.publicKey.toStringShort()], CHARLIE_NAME to dummyKey.toStringShort())

        assertEquals(results[alice2.publicKey.toStringShort()], ALICE_NAME to alice.publicKey.toStringShort())
        assertEquals(results[bob2.publicKey.toStringShort()], BOB_NAME to bob.publicKey.toStringShort())
        assertEquals(results[charlie2.publicKey.toStringShort()], CHARLIE_NAME to dummyKey.toStringShort())
    }
}
package net.corda.node.migration

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.PartyAndCertificate
import net.corda.coretesting.internal.rigorousMock
import net.corda.node.services.api.SchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.testing.core.*
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

class PersistentIdentityMigrationNewTableTest {
    companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bankOfCorda = TestIdentity(BOC_NAME)
        val bob = TestIdentity(BOB_NAME, 80)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ALICE_IDENTITY get() = alice.identity
        val BOB_IDENTITY get() = bob.identity
        val BOC_IDENTITY get() = bankOfCorda.identity
        val bob2 = TestIdentity(BOB_NAME, 40)
        val BOB2_IDENTITY = bob2.identity

        @ClassRule
        @JvmField
        val testSerialization = SerializationEnvironmentRule()
    }

    lateinit var liquibaseDB: H2Database
    lateinit var cordaDB: CordaPersistence

    @Before
    fun setUp() {
        val schemaService = rigorousMock<SchemaService>()
        doReturn(setOf(IdentityTestSchemaV1)).whenever(schemaService).schemas
        System.setProperty(SchemaMigration.NODE_X500_NAME, BOB_IDENTITY.name.toString())

        cordaDB = configureDatabase(
                MockServices.makeTestDataSourceProperties(),
                DatabaseConfig(),
                { null },
                { null },
                schemaService = schemaService,
                internalSchemas = setOf(),
                ourName = BOB_IDENTITY.name)
        liquibaseDB = H2Database()
        liquibaseDB.connection = JdbcConnection(cordaDB.dataSource.connection)
        liquibaseDB.isAutoCommit = true
    }

    @After
    fun close() {
        cordaDB.close()
    }

    @Test(timeout = 300_000)
    fun `migrate identities to new table`() {
        val identities = listOf(BOB_IDENTITY, ALICE_IDENTITY, BOC_IDENTITY, dummyNotary.identity, BOB2_IDENTITY)
        saveAllIdentities(identities)

        PersistentIdentityMigrationNewTable().execute(liquibaseDB)

        val expectedParties = identities.map { it.owningKey.toStringShort() to it.toString() }
        val actualParties = selectAll<IdentityTestSchemaV1.NodeIdentitiesNoCert>().map { it.publicKeyHash to it.name }
        assertThat(actualParties).isEqualTo(expectedParties)

        val expectedKeys = listOf(ALICE_IDENTITY, BOC_IDENTITY, dummyNotary.identity).map { it.owningKey.toStringShort() to it.owningKey }
        val actualKeys = selectAll<IdentityTestSchemaV1.NodeHashToKey>().map { it.publicKeyHash to Crypto.decodePublicKey(it.publicKey) }
        assertThat(actualKeys).isEqualTo(expectedKeys)
    }

    private fun saveAllIdentities(identities: List<PartyAndCertificate>) {
        cordaDB.transaction {
            identities.forEach {
                session.save(IdentityTestSchemaV1.NodeIdentities(it.owningKey.toStringShort(), it.certPath.encoded))
            }
        }
    }

    private inline fun <reified T> selectAll(): List<T> {
        return cordaDB.transaction {
            val criteria = session.criteriaBuilder.createQuery(T::class.java)
            criteria.select(criteria.from(T::class.java))
            session.createQuery(criteria).resultList
        }
    }
}
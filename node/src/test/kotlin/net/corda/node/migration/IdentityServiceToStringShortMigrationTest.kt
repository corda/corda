package net.corda.node.migration

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.hash
import net.corda.core.utilities.contextLogger
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import net.corda.testing.core.*
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.TestClock
import org.hamcrest.Matchers.`is`
import org.junit.*
import org.mockito.Mockito
import java.time.Clock

/**
 * These tests aim to verify that migrating vault states from V3 to later versions works correctly. While these unit tests verify the
 * migrating behaviour is correct (tables populated, columns updated for the right states), it comes with a caveat: they do not test that
 * deserialising states with the attachment classloader works correctly.
 *
 * The reason for this is that it is impossible to do so. There is no real way of writing a unit or integration test to upgrade from one
 * version to another (at the time of writing). These tests simulate a small part of the upgrade process by directly using hibernate to
 * populate a database as a V3 node would, then running the migration class. However, it is impossible to do this for attachments as there
 * is no contract state jar to serialise.
 */
class IdentityServiceToStringShortMigrationTest {
    companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bankOfCorda = TestIdentity(BOC_NAME)
        val bob = TestIdentity(BOB_NAME, 80)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ALICE_IDENTITY get() = alice.identity
        val BOB get() = bob.party
        val BOB_IDENTITY get() = bob.identity
        val BOC_IDENTITY get() = bankOfCorda.identity
        val bob2 = TestIdentity(BOB_NAME, 40)
        val BOB2_IDENTITY = bob2.identity

        val clock: TestClock = TestClock(Clock.systemUTC())

        @ClassRule
        @JvmField
        val testSerialization = SerializationEnvironmentRule()

        val logger = contextLogger()
    }

    lateinit var liquibaseDB: Database
    lateinit var cordaDB: CordaPersistence
    lateinit var notaryServices: MockServices

    @Before
    fun setUp() {
        cordaDB = configureDatabase(
                makeTestDataSourceProperties(),
                DatabaseConfig(),
                { _ -> null },
                { _ -> null },
                ourName = BOB_IDENTITY.name)
        val liquibaseConnection = Mockito.mock(JdbcConnection::class.java)
        Mockito.`when`(liquibaseConnection.url).thenReturn(cordaDB.jdbcUrl)
        Mockito.`when`(liquibaseConnection.wrappedConnection).thenReturn(cordaDB.dataSource.connection)
        liquibaseDB = Mockito.mock(Database::class.java)
        Mockito.`when`(liquibaseDB.connection).thenReturn(liquibaseConnection)
    }

    @After
    fun close() {
        contextTransactionOrNull?.close()
        cordaDB.close()
    }

    private fun saveAllIdentitiesWithOldHashString(identities: List<PartyAndCertificate>) {
        cordaDB.transaction {
            val groupedIdentities = identities.groupBy { it.name }
            groupedIdentities.forEach { name, certs ->
                val persistentIDs = certs.map { PersistentIdentityService.PersistentIdentity(it.owningKey.hash.toString(), it.certPath.encoded) }
                val persistentName = PersistentIdentityService.PersistentIdentityNames(name.toString(), certs.first().owningKey.hash.toString())
                persistentIDs.forEach {
                    session.persist(it)
                }
                session.persist(persistentName)
            }
        }
    }

    @Test
    fun `Check a simple migration works`() {
        val identities = listOf(BOB_IDENTITY, ALICE_IDENTITY, BOC_IDENTITY, dummyNotary.identity, BOB2_IDENTITY)
        saveAllIdentitiesWithOldHashString(identities)
        val migration = PersistentIdentityMigration()
        migration.execute(liquibaseDB)

        identities.forEach {
            println("Checking: ${it.name}")
            cordaDB.transaction {
                val statement = database.dataSource.connection.prepareStatement("SELECT pk_hash FROM ${NODE_DATABASE_PREFIX}identities WHERE pk_hash=?")
                statement.setString(1, it.owningKey.toStringShort())
                val rs = statement.executeQuery()
                Assert.assertThat(rs.next(), `is`(true))
                Assert.assertThat(rs.getString(1), `is`(it.owningKey.toStringShort()))
            }
        }
    }
}


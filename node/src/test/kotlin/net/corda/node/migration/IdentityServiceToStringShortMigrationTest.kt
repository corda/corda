package net.corda.node.migration

import liquibase.database.core.H2Database
import liquibase.database.jvm.JdbcConnection
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.hash
import net.corda.core.utilities.contextLogger
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.contextTransactionOrNull
import net.corda.testing.core.*
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
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
        val logger = contextLogger()
    }

    lateinit var liquibaseDB: H2Database
    lateinit var cordaDB: CordaPersistence

    @Before
    fun setUp() {
        cordaDB = configureDatabase(
                makeTestDataSourceProperties(),
                DatabaseConfig(),
                { null },
                { null },
                ourName = BOB_IDENTITY.name)
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

    private fun saveAllIdentitiesWithOldHashString(identities: List<PartyAndCertificate>) {
        cordaDB.transaction {
            val groupedIdentities = identities.groupBy { it.name }
            groupedIdentities.forEach { name, certs ->
                val persistentIDs = certs.map { PersistentIdentityService.PersistentPublicKeyHashToCertificate(it.owningKey.hash.toString(), it.certPath.encoded) }
                val persistentName = PersistentIdentityService.PersistentPartyToPublicKeyHash(name.toString(), certs.first().owningKey.hash.toString())
                persistentIDs.forEach {
                    session.persist(it)
                }
                session.persist(persistentName)
            }
        }
    }

    @Test
    fun `it should be possible to migrate all existing identities to new hash function`() {
        val identities = listOf(BOB_IDENTITY, ALICE_IDENTITY, BOC_IDENTITY, dummyNotary.identity, BOB2_IDENTITY)
        val groupedByNameIdentities = identities.groupBy { it.name }
        saveAllIdentitiesWithOldHashString(identities)
        val migration = PersistentIdentityMigration()
        liquibaseDB.execute(migration.generateStatements(liquibaseDB), listOf())
        val listOfNamesWithoutPkHash = mutableListOf<CordaX500Name>()
        identities.forEach {
            logger.info("Checking: ${it.name}")
            cordaDB.transaction {
                val hashToIdentityStatement = database.dataSource.connection.prepareStatement("SELECT ${PersistentIdentityService.PK_HASH_COLUMN_NAME} FROM ${PersistentIdentityService.HASH_TO_IDENTITY_TABLE_NAME} WHERE pk_hash=?")
                hashToIdentityStatement.setString(1, it.owningKey.toStringShort())
                val hashToIdentityResultSet = hashToIdentityStatement.executeQuery()

                //check that there is a row for every "new" hash
                Assert.assertThat(hashToIdentityResultSet.next(), `is`(true))
                //check that the pk_hash actually matches what we expect (kinda redundant, but deserializing the whole PartyAndCertificate feels like overkill)
                Assert.assertThat(hashToIdentityResultSet.getString(1), `is`(it.owningKey.toStringShort()))

                val nameToHashStatement = connection.prepareStatement("SELECT ${PersistentIdentityService.NAME_COLUMN_NAME} FROM ${PersistentIdentityService.NAME_TO_HASH_TABLE_NAME} WHERE pk_hash=?")
                nameToHashStatement.setString(1, it.owningKey.toStringShort())
                val nameToHashResultSet = nameToHashStatement.executeQuery()

                //if there is no result for this key, this means its an identity that is not stored in the DB (IE, it's been seen after another identity has already been mapped to it)
                if (nameToHashResultSet.next()) {
                    Assert.assertThat(nameToHashResultSet.getString(1), `is`(anyOf(groupedByNameIdentities.getValue(it.name).map<PartyAndCertificate, Matcher<String>?> { identity -> CoreMatchers.equalTo(identity.name.toString()) })))
                } else {
                    logger.warn("did not find a PK_HASH for ${it.name}")
                    listOfNamesWithoutPkHash.add(it.name)
                }
            }
        }


        listOfNamesWithoutPkHash.forEach {
            //the only time an identity name does not have a PK_HASH is if there are multiple identities associated with that name
            Assert.assertThat(groupedByNameIdentities[it]?.size, `is`(greaterThan(1)))
        }
    }
}


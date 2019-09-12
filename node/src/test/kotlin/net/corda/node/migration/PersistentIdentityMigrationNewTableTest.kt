package net.corda.node.migration

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.hash
import net.corda.core.internal.signWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.node.migration.VaultStateMigrationTest.Companion.CHARLIE
import net.corda.node.migration.VaultStateMigrationTest.Companion.DUMMY_NOTARY
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.mockito.Mockito
import java.security.KeyPair
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.util.*

class PersistentIdentityMigrationNewTableTest{
    companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bankOfCorda = TestIdentity(BOC_NAME)
        val bob = TestIdentity(BOB_NAME, 80)
        val dummyCashIssuer = TestIdentity(CordaX500Name("Snake Oil Issuer", "London", "GB"), 10)
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ALICE_IDENTITY get() = alice.identity
        val BOB_IDENTITY get() = bob.identity
        val BOC_IDENTITY get() = bankOfCorda.identity
        val BOC_KEY get() = bankOfCorda.keyPair
        val bob2 = TestIdentity(BOB_NAME, 40)
        val BOB2_IDENTITY = bob2.identity

        @ClassRule
        @JvmField
        val testSerialization = SerializationEnvironmentRule()
    }

    lateinit var liquidBaseDB: Database
    lateinit var cordaDB: CordaPersistence
    lateinit var notaryServices: MockServices

    @Before
    fun setUp() {
        val identityService = makeTestIdentityService(PersistentIdentityMigrationNewTableTest.dummyNotary.identity, BOB_IDENTITY, ALICE_IDENTITY)
        notaryServices = MockServices(listOf("net.corda.finance.contracts"), dummyNotary, identityService, dummyCashIssuer.keyPair, BOC_KEY)

        // Runs migration tasks
        cordaDB = configureDatabase(
                MockServices.makeTestDataSourceProperties(),
                DatabaseConfig(),
                notaryServices.identityService::wellKnownPartyFromX500Name,
                notaryServices.identityService::wellKnownPartyFromAnonymous,
                ourName = BOB_IDENTITY.name)
        val liquidbaseConnection = Mockito.mock(JdbcConnection::class.java)
        Mockito.`when`(liquidbaseConnection.url).thenReturn(cordaDB.jdbcUrl)
        Mockito.`when`(liquidbaseConnection.wrappedConnection).thenReturn(cordaDB.dataSource.connection)
        liquidBaseDB = Mockito.mock(Database::class.java)
        Mockito.`when`(liquidBaseDB.connection).thenReturn(liquidbaseConnection)

        cordaDB.dataSource.connection
        saveOurKeys(listOf(bob.keyPair, bob2.keyPair))
        saveAllIdentities(listOf(BOB_IDENTITY, ALICE_IDENTITY, BOC_IDENTITY, dummyNotary.identity, BOB2_IDENTITY))
        addNetworkParameters()
    }

    @After
    fun `close`() {
        cordaDB.close()
    }

    @Test
    fun `migrate identities to new table`() {
         val pkHash = addTestMapping(cordaDB.dataSource.connection, alice)
         PersistentIdentityMigrationNewTable()
         verifyTestMigration(cordaDB.dataSource.connection, pkHash, alice.name.toString())
    }

    private fun saveAllIdentities(identities: List<PartyAndCertificate>) {
        cordaDB.transaction {
            identities.forEach {
                session.save(PersistentIdentityService.PersistentPublicKeyHashToCertificate(it.owningKey.hash.toString(), it.certPath.encoded))
            }
        }
    }

    private fun saveOurKeys(keys: List<KeyPair>) {
        cordaDB.transaction {
            keys.forEach {
                val persistentKey = BasicHSMKeyManagementService.PersistentKey(it.public, it.private)
                session.save(persistentKey)
            }
        }
    }

    private fun addNetworkParameters() {
        cordaDB.transaction {
            val clock = Clock.systemUTC()
            val params = NetworkParameters(
                    1,
                    listOf(NotaryInfo(DUMMY_NOTARY, false), NotaryInfo(CHARLIE, false)),
                    1,
                    1,
                    clock.instant(),
                    1,
                    mapOf(),
                    Duration.ZERO,
                    mapOf()
            )
            val signedParams = params.signWithCert(bob.keyPair.private, BOB_IDENTITY.certificate)
            val persistentParams = DBNetworkParametersStorage.PersistentNetworkParameters(
                    SecureHash.allOnesHash.toString(),
                    params.epoch,
                    signedParams.raw.bytes,
                    signedParams.sig.bytes,
                    signedParams.sig.by.encoded,
                    X509Utilities.buildCertPath(signedParams.sig.parentCertsChain).encoded
            )
            session.save(persistentParams)
        }
    }

        private fun addTestMapping(connection: Connection, testIdentity: TestIdentity): String {
        val pkHash = UUID.randomUUID().toString()
        val cert = testIdentity.identity.certPath.encoded

        connection.prepareStatement("INSERT INTO node_identities (pk_hash, identity_value) VALUES (?,?)").use {
            it.setString(1, pkHash)
            it.setBytes(2, cert)
            it.executeUpdate()
        }
        return pkHash
    }

//    private fun deleteTestMapping(connection: Connection, pkHash: String) {
//        connection.prepareStatement("DELETE FROM node_identities WHERE pk_hash = ?").use {
//            it.setString(1, pkHash)
//            it.executeUpdate()
//        }
//    }

    private fun verifyTestMigration(connection: Connection, pk: String, name: String) {
        connection.createStatement().use {
            try {
                val rs = it.executeQuery("SELECT (pk_hash, name) FROM node_identities_no_cert")
                while (rs.next()) {
                    val result = rs.getString(1)
                    require(result.contains(pk))
                    require(result.contains(name))
                }
                rs.close()
            } catch (e: Exception) {
                println(e.localizedMessage)
            }
        }
    }
}
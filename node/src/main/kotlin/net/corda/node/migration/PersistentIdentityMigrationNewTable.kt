package net.corda.node.migration

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.*

/**
 * Migration that reads data from the [PersistentIdentityCert] table, extracts the parameters required to insert into the [PersistentIdentity] table.
 */
class PersistentIdentityMigrationNewTable : CordaMigration() {
    companion object {
        private val logger = contextLogger()
    }

    override fun execute(database: Database?) {
        logger.info("Migrating persistent identities with certificates table into persistent table with no certificate data.")

        if (database == null) {
            logger.error("Cannot migrate persistent states: Liquibase failed to provide a suitable database connection")
            throw PersistentIdentitiesMigrationException("Cannot migrate persistent states as liquibase failed to provide a suitable database connection")
        }
        initialiseNodeServices(database, setOf(PersistentIdentitiesMigrationSchemaBuilder.getMappedSchema()))

        val connection = database.connection as JdbcConnection

        doAndTestMigration(connection)
    }

    private fun doAndTestMigration(connection: JdbcConnection) {
        val alice = TestIdentity(CordaX500Name("Alice Corp", "Madrid", "ES"), 70)
        val pkHash = addTestMapping(connection, alice)

        // Extract data from old table needed to populate the new table
        val keyPartiesMap = extractKeyParties(connection)

        keyPartiesMap.forEach {
            insertEntry(connection, it)
        }

        verifyTestMigration(connection, pkHash, alice.name.toString())
        deleteTestMapping(connection, pkHash)
    }

    private fun extractKeyParties(connection: JdbcConnection): Map<String, CordaX500Name> {
        val keyParties = mutableMapOf<String, CordaX500Name>()
        connection.createStatement().use {
            val rs = it.executeQuery("SELECT pk_hash, identity_value FROM node_identities WHERE pk_hash IS NOT NULL")
            while (rs.next()) {
                val key = rs.getString(1)
                val partyBytes = rs.getBytes(2)
                val name = PartyAndCertificate(X509CertificateFactory().delegate.generateCertPath(partyBytes.inputStream())).party.name
                keyParties.put(key, name)
            }
            rs.close()
        }
        return keyParties
    }

    private fun insertEntry(connection: JdbcConnection, entry: Map.Entry<String, CordaX500Name>) {
        val pk = entry.key
        val name = entry.value.toString()
        connection.prepareStatement("INSERT INTO node_identities_no_cert (pk_hash, name) VALUES (?,?)").use {
            it.setString(1, pk)
            it.setString(2, name)
            it.executeUpdate()
        }
    }

    override fun setUp() {
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
    }

    override fun getConfirmationMessage(): String? {
        return null
    }

    override fun validate(database: Database?): ValidationErrors? {
        return null
    }

    private fun addTestMapping(connection: JdbcConnection, testIdentity: TestIdentity): String {
        val pkHash = UUID.randomUUID().toString()
        val cert = testIdentity.identity.certPath.encoded

        connection.prepareStatement("INSERT INTO node_identities (pk_hash, identity_value) VALUES (?,?)").use {
            it.setString(1, pkHash)
            it.setBytes(2, cert)
            it.executeUpdate()
        }
        return pkHash
    }

    private fun deleteTestMapping(connection: JdbcConnection, pkHash: String) {
        connection.prepareStatement("DELETE FROM node_identities WHERE pk_hash = ?").use {
            it.setString(1, pkHash)
            it.executeUpdate()
        }
    }

    private fun verifyTestMigration(connection: JdbcConnection, pk: String, name: String) {
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
                logger.error(e.localizedMessage)
            }
        }
    }
}

/**
 * A minimal set of schema for retrieving data from the database.
 *
 * Note that adding an extra schema here may cause migrations to fail if it ends up creating a table before the same table
 * is created in a migration script. As such, this migration must be run after the tables for the following have been created (and,
 * if they are removed in the future, before they are deleted).
 */
object PersistentIdentitiesMigrationSchema

object PersistentIdentitiesMigrationSchemaBuilder {
    fun getMappedSchema() =
            MappedSchema(schemaFamily = PersistentIdentitiesMigrationSchema.javaClass, version = 1,
                mappedTypes = listOf(
                    DBTransactionStorage.DBTransaction::class.java,
                    PersistentIdentityService.PersistentPublicKeyHashToCertificate::class.java,
                    PersistentIdentityService.PersistentPartyToPublicKeyHash::class.java,
                    PersistentIdentityService.PersistentPublicKeyHashToParty::class.java,
                    BasicHSMKeyManagementService.PersistentKey::class.java,
                    NodeAttachmentService.DBAttachment::class.java,
                    DBNetworkParametersStorage.PersistentNetworkParameters::class.java
            ))
}

class PersistentIdentitiesMigrationException(msg: String, cause: Exception? = null) : Exception(msg, cause)

/**
 * A class that encapsulates a test identity containing a [CordaX500Name] and a [KeyPair]. Duplicate of [net.corda.testing.core.TestIdentity]
 * to avoid circular dependencies.
 */
private class TestIdentity(val name: CordaX500Name, val keyPair: KeyPair) {

    /** Creates an identity with a deterministic [keyPair] i.e. same [entropy] same keyPair. */
    @JvmOverloads
    constructor(name: CordaX500Name, entropy: Long, signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME)
            : this(name, Crypto.deriveKeyPairFromEntropy(signatureScheme, BigInteger.valueOf(entropy)))

    val publicKey: PublicKey get() = keyPair.public
    val party: Party = Party(name, publicKey)
    val identity: PartyAndCertificate by lazy { getTestPartyAndCertificate(party) } // Often not needed.

    fun getTestPartyAndCertificate(party: Party): PartyAndCertificate {
        val trustRoot: X509Certificate = DEV_ROOT_CA.certificate
        val intermediate: CertificateAndKeyPair = DEV_INTERMEDIATE_CA

        val (nodeCaCert, nodeCaKeyPair) = createDevNodeCa(intermediate, party.name)

        val identityCert = X509Utilities.createCertificate(
                CertificateType.LEGAL_IDENTITY,
                nodeCaCert,
                nodeCaKeyPair,
                party.name.x500Principal,
                party.owningKey)

        val certPath = X509Utilities.buildCertPath(identityCert, nodeCaCert, intermediate.certificate, trustRoot)
        return PartyAndCertificate(certPath)
    }
}
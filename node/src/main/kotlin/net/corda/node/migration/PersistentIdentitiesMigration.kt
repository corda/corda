package net.corda.node.migration

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.nodeapi.internal.crypto.X509CertificateFactory

class PersistentIdentitiesMigration : CordaMigration() {

    companion object {
        private val logger = contextLogger()
    }

    override fun execute(database: Database?) {
        logger.info("Migrating persistent identities with certificates table into persistent table with no certificate data.")

        if (database == null) {
            logger.error("Cannot migrate persistent states: Liquibase failed to provide a suitable database connection")
            throw PersistentIdentitiesMigrationException("Cannot migrate persistent states as liquibase failed to provide a suitable database connection")
        }
        initialiseNodeServices(database, setOf(PersistentIdentitiesMigrationSchemaV1))

        val connection = database?.connection as JdbcConnection
        val keys = extractKeys(connection)
        val parties = extractParties(connection)

        keys.forEach {
            insertKey(connection, it)
        }
        parties.forEach {
            insertParty(connection, it)
        }
    }

    private fun insertParty(connection: JdbcConnection, name: CordaX500Name) {
        connection.prepareStatement("INSERT INTO node_identities_no_cert (name) WHERE name = ?").use {
            it.setString(1, name.toString())
        }
    }

    private fun insertKey(connection: JdbcConnection, key: String) {
        connection.prepareStatement("INSERT INTO node_identities_no_cert (pk_hash) WHERE pk_hash = ?").use {
            it.setString(1, key)
        }
    }

    private fun extractKeys(connection: JdbcConnection): List<String> {
        val keys = mutableListOf<String>()
        connection.createStatement().use {
            val rs = it.executeQuery("SELECT pk_hash FROM node_identities WHERE pk_hash IS NOT NULL")
            while(rs.next()) {
                val key = rs.getString(1)
                keys.add(key)
            }
        rs.close()
        }
        return keys
    }

    private fun extractParties(connection: JdbcConnection): List<CordaX500Name> {
        val entries = mutableListOf<ByteArray>()
        connection.createStatement().use {
            val rs = it.executeQuery("SELECT identity_value FROM node_identities")
            while (rs.next()) {
                val e = rs.getBytes(1)
                entries.add(e)
            }
        }
        return entries.map {
            PartyAndCertificate(X509CertificateFactory().delegate.generateCertPath(it.inputStream())).party.name
        }.toList()
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

object PersistentIdentitiesMigrationSchemaV1 : MappedSchema(schemaFamily = PersistentIdentitiesMigrationSchema.javaClass, version = 1,
        mappedTypes = listOf(
                DBTransactionStorage.DBTransaction::class.java,
                PersistentIdentityService.PersistentIdentityCert::class.java,
                PersistentIdentityService.PersistentIdentity::class.java,
                BasicHSMKeyManagementService.PersistentKey::class.java,
                NodeAttachmentService.DBAttachment::class.java,
                DBNetworkParametersStorage.PersistentNetworkParameters::class.java
        )
)

class PersistentIdentitiesMigrationException(msg: String, cause: Exception? = null) : Exception(msg, cause)

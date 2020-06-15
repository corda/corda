package net.corda.node.migration

import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.persistence.SchemaMigration
import java.security.PublicKey

/**
 * Migration that reads data from the [PersistentIdentityCert] table, extracts the parameters required to insert into the [PersistentIdentity] table.
 */
class PersistentIdentityMigrationNewTable : CustomTaskChange {
    companion object {
        private val logger = contextLogger()
    }

    private lateinit var ourName: CordaX500Name

    override fun execute(database: Database?) {
        logger.info("Migrating persistent identities with certificates table into persistent table with no certificate data.")

        if (database == null) {
            logger.error("Cannot migrate persistent identities: Liquibase failed to provide a suitable database connection")
            throw PersistentIdentitiesMigrationException("Cannot migrate persistent identities as liquibase failed to provide a suitable database connection")
        }
        ourName = CordaX500Name.parse(System.getProperty(SchemaMigration.NODE_X500_NAME))

        val connection = database.connection as JdbcConnection
        val hashToKeyAndName = extractKeyAndName(connection)

        hashToKeyAndName.forEach {
            insertEntry(connection, it)
        }
    }

    private fun extractKeyAndName(connection: JdbcConnection): Map<String, Pair<CordaX500Name, PublicKey>> {
        val rows = mutableMapOf<String, Pair<CordaX500Name, PublicKey>>()
        connection.createStatement().use {
            val rs = it.executeQuery("SELECT pk_hash, identity_value FROM node_identities WHERE pk_hash IS NOT NULL")
            while (rs.next()) {
                val publicKeyHash = rs.getString(1)
                val certificateBytes = rs.getBytes(2)
                // Deserialise certificate.
                val certPath = X509CertificateFactory().delegate.generateCertPath(certificateBytes.inputStream())
                val partyAndCertificate = PartyAndCertificate(certPath)
                // Record name and public key.
                val publicKey = partyAndCertificate.certificate.publicKey
                val name = partyAndCertificate.name
                rows[publicKeyHash] = Pair(name, publicKey)
            }
            rs.close()
        }
        return rows
    }

    private fun insertEntry(connection: JdbcConnection, entry: Map.Entry<String, Pair<CordaX500Name, PublicKey>>) {
        val publicKeyHash = entry.key
        val (name, publicKey) = entry.value
        connection.prepareStatement("INSERT INTO node_identities_no_cert (pk_hash, name) VALUES (?,?)").use {
            it.setString(1, publicKeyHash)
            it.setString(2, name.toString())
            it.executeUpdate()
        }
        if (name != ourName) {
            connection.prepareStatement("INSERT INTO node_hash_to_key (pk_hash, public_key) VALUES (?,?)").use {
                it.setString(1, publicKeyHash)
                it.setBytes(2, publicKey.encoded)
                it.executeUpdate()
            }
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
}

class PersistentIdentitiesMigrationException(msg: String, cause: Exception? = null) : Exception(msg, cause)
package net.corda.node.migration

import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import org.apache.commons.lang3.ArrayUtils
import java.sql.ResultSet

class NodeIdentitiesNoCertMigration : CustomTaskChange {
    companion object {
        private val logger = contextLogger()
    }

    @Suppress("MagicNumber")
    override fun execute(database: Database) {
        val connection = database.connection as JdbcConnection

        logger.info("Preparing to migrate node_identities_no_cert.")

        val nodeKeysByHash = mutableMapOf<String, ByteArray>()
        connection.queryAll("SELECT pk_hash, identity_value FROM node_identities") { resultSet ->
            val hash = resultSet.getString(1)
            val certificateBytes = resultSet.getBytes(2)
            nodeKeysByHash[hash] = certificateBytes.toKeyBytes()
        }

        val nodeKeyHashesByName = mutableMapOf<String, String>()
        connection.queryAll("SELECT name, pk_hash FROM node_named_identities") { resultSet ->
            val name = resultSet.getString(1)
            val hash = resultSet.getString(2)
            nodeKeyHashesByName[name] = hash
        }

        logger.info("Starting to migrate node_identities_no_cert.")

        var count = 0
        connection.queryAll("SELECT pk_hash, name FROM node_identities_no_cert") { resultSet ->
            val hash = resultSet.getString(1)
            val name = resultSet.getString(2)

            val partyKeyHash = nodeKeysByHash[hash]?.let { hash }
                    ?: nodeKeyHashesByName[name]
                    ?: "".also { logger.warn("Unable to find party key hash for [$name] [$hash]") }

            val key = nodeKeysByHash[hash]
                    ?: connection.query("SELECT public_key FROM v_our_key_pairs WHERE public_key_hash = ?", hash)
                    ?: connection.query("SELECT public_key FROM node_hash_to_key WHERE pk_hash = ?", hash)
                    ?: ArrayUtils.EMPTY_BYTE_ARRAY.also { logger.warn("Unable to find key for [$name] [$hash]") }

            connection.prepareStatement("UPDATE node_identities_no_cert SET party_pk_hash = ?, public_key = ? WHERE pk_hash = ?").use {
                it.setString(1, partyKeyHash)
                it.setBytes(2, key)
                it.setString(3, hash)
                it.executeUpdate()
            }
            count++
        }

        logger.info("Migrated $count node_identities_no_cert entries.")
    }

    private fun JdbcConnection.queryAll(statement: String, action: (ResultSet) -> Unit) = createStatement().use {
        it.executeQuery(statement).use { resultSet ->
            while (resultSet.next()) {
                action.invoke(resultSet)
            }
        }
    }

    private fun JdbcConnection.query(statement: String, key: String): ByteArray? = prepareStatement(statement).use {
        it.setString(1, key)
        it.executeQuery().use { resultSet ->
            if (resultSet.next()) resultSet.getBytes(1) else null
        }
    }

    private fun ByteArray.toKeyBytes(): ByteArray {
        val certPath = X509CertificateFactory().delegate.generateCertPath(inputStream())
        val partyAndCertificate = PartyAndCertificate(certPath)
        return partyAndCertificate.party.owningKey.encoded
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

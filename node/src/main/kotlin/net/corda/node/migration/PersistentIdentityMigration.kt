package net.corda.node.migration

import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import java.sql.Connection

class PersistentIdentityMigration : CustomTaskChange {
    companion object {
        private val logger = contextLogger()
        const val PUB_KEY_HASH_TO_PARTY_AND_CERT_TABLE = "${NODE_DATABASE_PREFIX}identities"
        const val X500_NAME_TO_PUB_KEY_HASH_TABLE = "${NODE_DATABASE_PREFIX}named_identities"
    }

    override fun execute(db: Database?) {
        val dataSource = MigrationDataSource(db!!)
        val connection = dataSource.connection
        val statement = connection.prepareStatement("SELECT * FROM ${NODE_DATABASE_PREFIX}identities")
        val resultSet = statement.executeQuery()
        val mappedResults = mutableListOf<MigrationData>()
        while (resultSet.next()) {
            val oldPkHash = resultSet.getString(1)
            val identityBytes = resultSet.getBytes(2)
            val partyAndCertificate = PartyAndCertificate(X509CertificateFactory().delegate.generateCertPath(identityBytes.inputStream()))
            mappedResults.add(MigrationData(oldPkHash, partyAndCertificate))
        }
        mappedResults.forEach {
            updateHashToIdentityRow(connection, it)
            updateNameToHashRow(connection, it)
        }


        logger.info("Migrating PersistentIdentityService to use PublicKey.toShortString()")
    }

    override fun validate(database: Database?): ValidationErrors? {
        return null
    }

    override fun getConfirmationMessage(): String? {
        return null
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
    }

    override fun setUp() {
    }

    private fun updateHashToIdentityRow(connection: Connection, migrationData: MigrationData) {
        connection.prepareStatement("UPDATE $PUB_KEY_HASH_TO_PARTY_AND_CERT_TABLE SET pk_hash = ? WHERE pk_hash = ?").use {
            it.setString(1, migrationData.newPkHash)
            it.setString(2, migrationData.oldPkHash)
            it.executeUpdate()
        }
    }

    private fun updateNameToHashRow(connection: Connection, migrationData: MigrationData) {
        connection.prepareStatement("UPDATE $X500_NAME_TO_PUB_KEY_HASH_TABLE SET pk_hash = ? WHERE pk_hash = ? AND name= ?").use {
            it.setString(1, migrationData.newPkHash)
            it.setString(2, migrationData.oldPkHash)
            it.setString(3, migrationData.x500.toString())
            it.executeUpdate()
        }
    }

    data class MigrationData(val oldPkHash: String,
                             val partyAndCertificate: PartyAndCertificate,
                             val x500: CordaX500Name = partyAndCertificate.name,
                             val newPkHash: String = partyAndCertificate.owningKey.toStringShort())
}
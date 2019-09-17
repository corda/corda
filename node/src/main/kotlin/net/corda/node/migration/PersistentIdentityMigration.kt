package net.corda.node.migration

import liquibase.change.custom.CustomSqlChange
import liquibase.database.Database
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import liquibase.statement.core.UpdateStatement
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.utilities.contextLogger
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.nodeapi.internal.crypto.X509CertificateFactory

class PersistentIdentityMigration : CustomSqlChange {

    companion object {
        private val logger = contextLogger()
        const val PUB_KEY_HASH_TO_PARTY_AND_CERT_TABLE = PersistentIdentityService.HASH_TO_IDENTITY_TABLE_NAME
        const val X500_NAME_TO_PUB_KEY_HASH_TABLE = PersistentIdentityService.NAME_TO_HASH_TABLE_NAME
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

    override fun generateStatements(database: Database?): Array<SqlStatement> {
        val dataSource = MigrationDataSource(database!!)
        val connection = dataSource.connection
        val statement = connection.prepareStatement("SELECT * FROM $PUB_KEY_HASH_TO_PARTY_AND_CERT_TABLE")
        val resultSet = statement.executeQuery()
        val generatedStatements = mutableListOf<SqlStatement>()
        while (resultSet.next()) {
            val oldPkHash = resultSet.getString(1)
            val identityBytes = resultSet.getBytes(2)
            val partyAndCertificate = PartyAndCertificate(X509CertificateFactory().delegate.generateCertPath(identityBytes.inputStream()))
            generatedStatements.addAll(MigrationData(oldPkHash, partyAndCertificate).let {
                listOf(
                        updateHashToIdentityRow(it, dataSource),
                        updateNameToHashRow(it, dataSource)
                )
            })
        }
        return generatedStatements.toTypedArray()
    }

    private fun updateHashToIdentityRow(migrationData: MigrationData, dataSource: MigrationDataSource): SqlStatement {
        return UpdateStatement(dataSource.connection.catalog, dataSource.connection.schema, PUB_KEY_HASH_TO_PARTY_AND_CERT_TABLE)
                .setWhereClause("pk_hash=?")
                .addNewColumnValue("pk_hash", migrationData.newPkHash)
                .addWhereParameter(migrationData.oldPkHash)
    }

    private fun updateNameToHashRow(migrationData: MigrationData, dataSource: MigrationDataSource): UpdateStatement {
        return UpdateStatement(dataSource.connection.catalog, dataSource.connection.schema, X500_NAME_TO_PUB_KEY_HASH_TABLE)
                .setWhereClause("pk_hash=? AND name=?")
                .addNewColumnValue("pk_hash", migrationData.newPkHash)
                .addWhereParameters(migrationData.oldPkHash, migrationData.x500.toString())
    }

    data class MigrationData(val oldPkHash: String,
                             val partyAndCertificate: PartyAndCertificate,
                             val x500: CordaX500Name = partyAndCertificate.name,
                             val newPkHash: String = partyAndCertificate.owningKey.toStringShort())
}
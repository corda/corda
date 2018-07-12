package net.corda.nodeapi.internal.persistence

import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import java.sql.Connection

/**
 * Database table mapping [PhysicalNamingStrategy] to allow running against not migrated database from Corda 3.0/3.1 to newer versions.
 */
object AttachmentsContractsTableBackwardCompatibleNamingStrategy {
    private const val correctName = "NODE_ATTACHMENTS_CONTRACTS"
    private const val incorrectV30Name = "NODE_ATTACHMENTS_CONTRACT_CLASS_NAME"
    private const val incorrectV31Name = "NODE_ATTCHMENTS_CONTRACTS"
    private var detectedIncorrectName: String? = null

    private fun createStartegy(tableFromMapping: String, existingTable: String, databaseConfig: DatabaseConfig): PhysicalNamingStrategy? =
            object : PhysicalNamingStrategyStandardImpl() {
                override fun toPhysicalTableName(name: Identifier?, context: JdbcEnvironment?): Identifier {
                    val default = super.toPhysicalTableName(name, context)
                    val text = if (default.text.equals(tableFromMapping, true))
                        existingTable
                    else
                        default.text
                    return Identifier.toIdentifier(databaseConfig.serverNameTablePrefix + text, default.isQuoted)
                }
            }

    fun getNamingStrategy(connection: Connection, databaseConfig: DatabaseConfig): PhysicalNamingStrategy? =
            when {
                connection.metaData.getTables(null, null, correctName, null).next() -> null
                connection.metaData.getTables(null, null, incorrectV30Name, null).next() -> {
                    detectedIncorrectName = incorrectV30Name
                    createStartegy(correctName, incorrectV30Name, databaseConfig)
                }
                connection.metaData.getTables(null, null, incorrectV31Name, null).next() -> {
                    detectedIncorrectName = incorrectV31Name
                    createStartegy(correctName, incorrectV31Name, databaseConfig)
                }
                else -> null
            }

    fun incorrectNameDetected() = detectedIncorrectName != null

    fun getWarning() = if (incorrectNameDetected()) "Not migrated table name $detectedIncorrectName is found, try to migrate name $correctName, see Upgrade Notes." else ""
}
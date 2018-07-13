package net.corda.nodeapi.internal.persistence

import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import java.sql.Connection

interface NamingStrategyFactoryMethod {
    fun getNamingStrategy(connection: Connection, databaseConfig: DatabaseConfig) : PhysicalNamingStrategy
    fun getWarning() : String?
}

class DefaultNamingStrategy(private val databaseConfig: DatabaseConfig) : PhysicalNamingStrategyStandardImpl() {
    override fun toPhysicalTableName(name: Identifier?, context: JdbcEnvironment?): Identifier {
        val default = super.toPhysicalTableName(name, context)
        return Identifier.toIdentifier(databaseConfig.serverNameTablePrefix + default.text, default.isQuoted)
    }
}

object DefaultNamingStrategyFactory : NamingStrategyFactoryMethod {
    override fun getNamingStrategy(connection: Connection, databaseConfig: DatabaseConfig) = DefaultNamingStrategy(databaseConfig)
    override fun getWarning() = null
}

/**
 * Database table mapping [PhysicalNamingStrategy] to allow running against not migrated database from Corda 3.0/3.1 to newer versions.
 */
object CordaV30BackwardCompatibleNamingStrategyFactory : NamingStrategyFactoryMethod {
    private const val correctName = "NODE_ATTACHMENTS_CONTRACTS"
    private const val incorrectV30Name = "NODE_ATTACHMENTS_CONTRACT_CLASS_NAME"
    private const val incorrectV31Name = "NODE_ATTCHMENTS_CONTRACTS"
    private var detectedIncorrectName: String? = null

    private fun getNamingStrategy(tableFromMapping: String, existingTable: String, databaseConfig: DatabaseConfig): PhysicalNamingStrategy =
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

    override fun getNamingStrategy(connection: Connection, databaseConfig: DatabaseConfig): PhysicalNamingStrategy =
            when {
                connection.metaData.getTables(null, null, correctName, null).next() ->
                    DefaultNamingStrategy(databaseConfig)
                connection.metaData.getTables(null, null, incorrectV30Name, null).next() -> {
                    detectedIncorrectName = incorrectV30Name
                    getNamingStrategy(correctName, incorrectV30Name, databaseConfig)
                }
                connection.metaData.getTables(null, null, incorrectV31Name, null).next() -> {
                    detectedIncorrectName = incorrectV31Name
                    getNamingStrategy(correctName, incorrectV31Name, databaseConfig)
                }
                else -> DefaultNamingStrategy(databaseConfig)
            }

    override fun getWarning() = if (detectedIncorrectName != null)
        "The database contains older table name $detectedIncorrectName instead of $correctName, try to migrate your database as per Upgrade Notes." else null
}
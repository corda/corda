package net.corda.nodeapi.internal.persistence

import net.corda.core.utilities.contextLogger
import java.sql.Connection
import java.sql.SQLException

/**
 * Function to auto upgrade (for H2 database) or validate (for other databases) changes introduced to database schema.
 */
class DatabaseMigration {

    companion object {
        private val log = contextLogger()

        /** Upgrades (or validates for non H2) NODE_ATTACHMENTS_CONTRACTS table name to version 3.2 */
        fun validateOrAlterAttachmentsContractsTableName(connection: Connection) {
            val correctName = "NODE_ATTACHMENTS_CONTRACTS"
            val incorrectV30Name = "NODE_ATTACHMENTS_CONTRACT_CLASS_NAME"
            val incorrectV31Name = "NODE_ATTCHMENTS_CONTRACTS"

            fun warning(incorrectName: String, version: String) = "The database contains the older table name $incorrectName" +
                    " instead of $correctName, see upgrade notes to migrate from Corda database version $version https://docs.corda.net/head/upgrade-notes.html."

            if (!connection.metaData.getTables(null, null, correctName, null).next()) {
                val metaData = connection.metaData
                if (connection.metaData.getTables(null, null, incorrectV30Name, null).next()) {
                    if (metaData.databaseProductName == "H2") {
                        log.info("Changing table name $incorrectV30Name to $correctName")
                        connection.createStatement().execute("ALTER TABLE $incorrectV30Name RENAME TO $correctName")
                        connection.commit()
                    } else {
                        throw DatabaseIncompatibleException(warning(incorrectV30Name, "3.0"))
                    }
                }
                if (connection.metaData.getTables(null, null, incorrectV31Name, null).next()) {
                    if (metaData.databaseProductName == "H2") {
                        log.info("Changing table name $incorrectV30Name to $correctName")
                        connection.createStatement().execute("ALTER TABLE $incorrectV31Name RENAME TO $correctName")
                        connection.commit()
                    } else {
                        throw DatabaseIncompatibleException(warning(incorrectV31Name, "3.1"))
                    }
                }
            }
        }

        /** Upgrades (or validates for non H2) NODE_INFO_HOSTS table column name to version 4.0 */
        fun validateOrAlterInfoHostsTableColumnName(connection: Connection) =
                validateOrAlterColumn(connection, "NODE_INFO_HOSTS", "HOST_NAME", "HOST")

        private fun validateOrAlterColumn(connection: Connection, table: String, correctColumnName: String, incorrectColumnName: String) {
            fun warning() = "The database table $table contains the older column name $incorrectColumnName instead of $correctColumnName, " +
                    "see upgrade notes to migrate from Corda database version https://docs.corda.net/head/upgrade-notes.html."

            val metaData = connection.metaData
            val resultSet = metaData.getColumns(null, null, table, incorrectColumnName)
            if (resultSet.next()) {
                if (resultSet.getString("COLUMN_NAME") == incorrectColumnName) {
                    if (metaData.databaseProductName == "H2") {
                        try {
                            log.info("Changing column name $incorrectColumnName to $correctColumnName of table $table")
                            connection.createStatement().execute("ALTER TABLE $table ALTER COLUMN $incorrectColumnName RENAME TO $correctColumnName")
                            connection.commit()
                        } catch (exp: SQLException) {
                            throw DatabaseIncompatibleException("Failed to upgrade table $table. " + warning())
                        }
                    } else {
                        throw DatabaseIncompatibleException(warning())
                    }
                }
            }
        }

        /** Validates PostgreSQL node_checkpoints column type compatible with version 3.2 */
        fun checkCorrectCheckpointTypeOnPostgres(connection: Connection) {
            val metaData = connection.metaData
            if (metaData.databaseProductName != "PostgreSQL") {
                return
            }

            val result = metaData.getColumns(null, null, "node_checkpoints", "checkpoint_value")
            if (result.next()) {
                val type = result.getString("TYPE_NAME")
                if (type != "bytea") {
                    throw DatabaseIncompatibleException("The type of the 'checkpoint_value' table must be 'bytea', " +
                            "but 'oid' was found. See upgrade notes to migrate from Corda database version 3.1 https://docs.corda.net/head/upgrade-notes.html.")
                }
            }
        }
    }
}

class DatabaseIncompatibleException(override val message: String?, override val cause: Throwable? = null) : Exception()
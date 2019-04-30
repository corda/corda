package net.corda.nodeapi.internal.persistence

import  liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.utilities.contextLogger
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class ExternalIdTableMigration : CustomTaskChange {
    companion object {
        private val logger = contextLogger()
    }

    override fun execute(database: Database?) {
        val connection = database?.connection as JdbcConnection
        val pkHash = addEmptyMapping(connection)
        val existingMappings = getMappingsWithoutADate(connection)
        existingMappings.forEach {
            updateDate(connection, it)
        }
        deleteEmptyMapping(connection, pkHash)
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

    private fun addEmptyMapping(connection: JdbcConnection): String {
        val pkHash = UUID.randomUUID().toString()
        connection.prepareStatement("INSERT INTO pk_hash_to_ext_id_map (external_id, public_key_hash) VALUES (?,?)").use {
            it.setString(1, UUID.randomUUID().toString())
            it.setString(2, pkHash)
            it.executeUpdate()
        }
        return pkHash
    }

    private fun deleteEmptyMapping(connection: JdbcConnection, pkHash: String) {
        connection.prepareStatement("DELETE FROM pk_hash_to_ext_id_map WHERE public_key_hash = ?").use {
            it.setString(1, pkHash)
            it.executeUpdate()
        }
    }


    private fun getMappingsWithoutADate(connection: JdbcConnection): List<String> =
            connection.createStatement().use {
                val hashes = mutableListOf<String>()
                val rs = it.executeQuery("SELECT public_key_hash FROM pk_hash_to_ext_id_map WHERE date_mapped IS NULL")
                while (rs.next()) {
                    val elem = rs.getString(1)
                    hashes.add(elem)
                }
                rs.close()
                hashes
            }

    private fun updateDate(connection: JdbcConnection, hash: String) {
        connection.prepareStatement("UPDATE pk_hash_to_ext_id_map SET date_mapped = ? WHERE public_key_hash = ?").use {
            it.setTimestamp(1, Timestamp.from(Instant.EPOCH))
            it.setString(2, hash)
            it.executeUpdate()
        }
    }
}
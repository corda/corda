package net.corda.behave.node.configuration

import net.corda.behave.database.DatabaseType

data class DatabaseConfiguration(
        val type: DatabaseType,
        val host: String,
        val port: Int,
        val username: String = type.settings.userName,
        val password: String,
        val database: String = type.settings.databaseName,
        val schema: String = type.settings.schemaName
) {

    fun config() = type.settings.config(this)
}

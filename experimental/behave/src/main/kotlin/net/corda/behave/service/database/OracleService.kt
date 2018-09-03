package net.corda.behave.service.database

import net.corda.behave.database.DatabaseConnection
import net.corda.behave.database.DatabaseType
import net.corda.behave.database.configuration.OracleConfigurationTemplate
import net.corda.behave.node.configuration.DatabaseConfiguration
import net.corda.behave.service.ContainerService
import net.corda.behave.service.ServiceSettings

abstract class OracleService(
        name: String,
        port: Int,
        startupStatement: String,
        private val password: String,
        settings: ServiceSettings = ServiceSettings()
) : ContainerService(name, port, startupStatement, settings) {

    override val internalPort = 1521

    protected abstract val type: DatabaseType

    override fun verify(): Boolean {
        val config = DatabaseConfiguration(
                type = type,
                host = host,
                port = port,
                database = database,
                schema = schema,
                username = username,
                password = password
        )
        val connection = DatabaseConnection(config, OracleConfigurationTemplate())
        try {
            connection.use {
                return true
            }
        } catch (ex: Exception) {
            log.warn(ex.message, ex)
            ex.printStackTrace()
        }
        return false
    }

    companion object {
        val host = "localhost"
        val database = ""
        val schema = ""
        val username = "system"
        val password = "oracle"
    }
}
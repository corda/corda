package net.corda.behave.service.database

import net.corda.behave.database.DatabaseConnection
import net.corda.behave.database.DatabaseType
import net.corda.behave.database.configuration.PostgresConfigurationTemplate
import net.corda.behave.node.configuration.DatabaseConfiguration
import net.corda.behave.service.ContainerService
import net.corda.behave.service.ServiceSettings

class PostgreSQLService(
        name: String,
        port: Int,
        private val password: String,
        settings: ServiceSettings = ServiceSettings()
) : ContainerService(name, port, "database system is ready to accept connections", settings) {

    override val baseImage = "postgres"

    override val internalPort = 5432

    override fun verify(): Boolean {
        val config = DatabaseConfiguration(
                type = DatabaseType.POSTGRES,
                host = host,
                port = port,
                database = database,
                schema = schema,
                username = username,
                password = password
        )
        val connection = DatabaseConnection(config, PostgresConfigurationTemplate())
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
        val database = "postgres"
        val schema = "public"
        val username = "postgres"
        val driver = "postgresql-42.1.4.jar"
    }

}
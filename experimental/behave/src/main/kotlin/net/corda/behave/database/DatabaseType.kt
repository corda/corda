package net.corda.behave.database

import net.corda.behave.database.configuration.H2ConfigurationTemplate
import net.corda.behave.database.configuration.PostgresConfigurationTemplate
import net.corda.behave.node.configuration.Configuration
import net.corda.behave.node.configuration.DatabaseConfiguration
import net.corda.behave.service.database.H2Service
import net.corda.behave.service.database.PostgreSQLService

enum class DatabaseType(val settings: DatabaseSettings) {

    H2(DatabaseSettings()
            .withDatabase(H2Service.database)
            .withSchema(H2Service.schema)
            .withUser(H2Service.username)
            .withConfigTemplate(H2ConfigurationTemplate())
            .withServiceInitiator {
                H2Service("h2-${it.name}", it.database.port)
            }
    ),

    POSTGRES(DatabaseSettings()
            .withDatabase(PostgreSQLService.database)
            .withDriver(PostgreSQLService.driver)
            .withSchema(PostgreSQLService.schema)
            .withUser(PostgreSQLService.username)
            .withConfigTemplate(PostgresConfigurationTemplate())
            .withServiceInitiator {
                PostgreSQLService("postgres-${it.name}", it.database.port, it.database.password)
            }
    );

    val driverJar = settings.driverJar

    fun dependencies(config: Configuration) = settings.dependencies(config)

    fun connection(config: DatabaseConfiguration) = DatabaseConnection(config, settings.template)

    companion object {

        fun fromName(name: String): DatabaseType? = when (name.toLowerCase()) {
            "h2" -> H2
            "postgres" -> POSTGRES
            "postgresql" -> POSTGRES
            else -> null
        }
    }
}
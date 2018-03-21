package net.corda.behave.database

import net.corda.behave.database.configuration.H2ConfigurationTemplate
import net.corda.behave.database.configuration.PostgresConfigurationTemplate
import net.corda.behave.database.configuration.SqlServerConfigurationTemplate
import net.corda.behave.node.configuration.Configuration
import net.corda.behave.node.configuration.DatabaseConfiguration
import net.corda.behave.service.database.H2Service
import net.corda.behave.service.database.PostgreSQLService
import net.corda.behave.service.database.SqlServerService

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

    SQL_SERVER(DatabaseSettings()
            .withDatabase(SqlServerService.database)
            .withDriver(SqlServerService.driver)
            .withSchema(SqlServerService.schema)
            .withUser(SqlServerService.username)
            .withConfigTemplate(SqlServerConfigurationTemplate())
            .withServiceInitiator {
                SqlServerService("sqlserver-${it.name}", it.database.port, it.database.password)
            }
    ),

    POSTGRES(DatabaseSettings()
            .withDriver(PostgreSQLService.driver)
            .withSchema(PostgreSQLService.schema)
            .withUser(PostgreSQLService.username)
            .withConfigTemplate(PostgresConfigurationTemplate())
            .withServiceInitiator {
                PostgreSQLService("postgres-${it.name}", it.database.port, "postgres")
            }
    );

    val driverJar = settings.driverJar

    fun dependencies(config: Configuration) = settings.dependencies(config)

    fun connection(config: DatabaseConfiguration) = DatabaseConnection(config, settings.template)

    companion object {

        fun fromName(name: String): DatabaseType? = when (name.toLowerCase()) {
            "h2" -> H2
            "sql_server" -> SQL_SERVER
            "sql server" -> SQL_SERVER
            "sqlserver" -> SQL_SERVER
            "postgres" -> POSTGRES
            "postgreSQL" -> POSTGRES
            else -> null
        }

    }

}
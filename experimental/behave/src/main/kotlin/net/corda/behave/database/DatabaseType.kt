/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.database

import net.corda.behave.database.configuration.H2ConfigurationTemplate
import net.corda.behave.database.configuration.OracleConfigurationTemplate
import net.corda.behave.database.configuration.PostgresConfigurationTemplate
import net.corda.behave.database.configuration.SqlServerConfigurationTemplate
import net.corda.behave.node.configuration.Configuration
import net.corda.behave.node.configuration.DatabaseConfiguration
import net.corda.behave.service.database.*

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
                SqlServerService("sql-server-${it.name}", it.database.port, it.database.password)
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
    ),

    ORACLE_12C(DatabaseSettings()
        .withDriver(Oracle12cService.driver)
        .withSchema(OracleService.schema)
        .withUser(OracleService.username)
        .withConfigTemplate(OracleConfigurationTemplate())
        .withServiceInitiator {
            Oracle12cService("oracle-12c-${it.name}", it.database.port, it.database.password)
        }
    ),

    ORACLE_11G(DatabaseSettings()
        .withDriver(Oracle11gService.driver)
        .withSchema(OracleService.schema)
        .withUser(OracleService.username)
        .withConfigTemplate(OracleConfigurationTemplate())
        .withServiceInitiator {
            Oracle11gService("oracle-11g-${it.name}", it.database.port, it.database.password)
        }
    );

    val driverJar = settings.driverJar

    fun dependencies(config: Configuration) = settings.dependencies(config)

    fun connection(config: DatabaseConfiguration) = DatabaseConnection(config, settings.template)

    companion object {

        fun fromName(name: String): DatabaseType? = when (name.replace("[ _-]".toRegex(), "").toLowerCase()) {
            "h2" -> H2
            "sqlserver" -> SQL_SERVER
            "postgres" -> POSTGRES
            "postgresql" -> POSTGRES
            "oracle11" -> ORACLE_11G
            "oracle11g" -> ORACLE_11G
            "oracle12" -> ORACLE_12C
            "oracle12c" -> ORACLE_12C
            else -> null
        }
    }
}
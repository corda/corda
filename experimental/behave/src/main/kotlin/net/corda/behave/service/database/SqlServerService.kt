/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.service.database

import net.corda.behave.database.DatabaseConnection
import net.corda.behave.database.DatabaseType
import net.corda.behave.database.configuration.SqlServerConfigurationTemplate
import net.corda.behave.node.configuration.DatabaseConfiguration
import net.corda.behave.service.ContainerService
import net.corda.behave.service.ServiceSettings

class SqlServerService(
        name: String,
        port: Int,
        private val password: String,
        settings: ServiceSettings = ServiceSettings()
) : ContainerService(name, port, settings) {

    override val baseImage = "microsoft/mssql-server-linux"

    override val internalPort = 1433

    init {
        addEnvironmentVariable("ACCEPT_EULA", "Y")
        addEnvironmentVariable("SA_PASSWORD", password)
        setStartupStatement("SQL Server is now ready for client connections")
    }

    override fun verify(): Boolean {
        val config = DatabaseConfiguration(
                type = DatabaseType.SQL_SERVER,
                host = host,
                port = port,
                database = database,
                schema = schema,
                username = username,
                password = password
        )
        val connection = DatabaseConnection(config, SqlServerConfigurationTemplate())
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
        val database = "master"
        val schema = "dbo"
        val username = "sa"

    }

}
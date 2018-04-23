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

import net.corda.behave.node.configuration.Configuration
import net.corda.behave.node.configuration.DatabaseConfiguration
import net.corda.behave.service.Service
import net.corda.behave.service.ServiceInitiator

class DatabaseSettings {

    var databaseName: String = "node"
        private set

    var schemaName: String = "dbo"
        private set

    var userName: String = "sa"
        private set

    var driverJar: String? = null
        private set

    private var databaseConfigTemplate: DatabaseConfigurationTemplate = DatabaseConfigurationTemplate()

    private val serviceInitiators = mutableListOf<ServiceInitiator>()

    fun withDatabase(name: String): DatabaseSettings {
        databaseName = name
        return this
    }

    fun withSchema(name: String): DatabaseSettings {
        schemaName = name
        return this
    }

    fun withDriver(name: String): DatabaseSettings {
        driverJar = name
        return this
    }

    fun withUser(name: String): DatabaseSettings {
        userName = name
        return this
    }

    fun withServiceInitiator(initiator: ServiceInitiator): DatabaseSettings {
        serviceInitiators.add(initiator)
        return this
    }

    fun withConfigTemplate(configTemplate: DatabaseConfigurationTemplate): DatabaseSettings {
        databaseConfigTemplate = configTemplate
        return this
    }

    fun config(config: DatabaseConfiguration): String {
        return databaseConfigTemplate.generate(config)
    }

    fun dependencies(config: Configuration): List<Service> {
        return serviceInitiators.map { it(config) }
    }

    val template: DatabaseConfigurationTemplate
        get() = databaseConfigTemplate

}

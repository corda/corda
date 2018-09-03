package net.corda.behave.database

import net.corda.behave.node.configuration.DatabaseConfiguration

open class DatabaseConfigurationTemplate {

    open val connectionString: (DatabaseConfiguration) -> String = { "" }

    protected open val config: (DatabaseConfiguration) -> String = { "" }

    fun generate(config: DatabaseConfiguration) = config(config).trimMargin()
}
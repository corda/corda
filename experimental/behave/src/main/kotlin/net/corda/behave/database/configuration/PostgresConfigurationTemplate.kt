package net.corda.behave.database.configuration

import net.corda.behave.database.DatabaseConfigurationTemplate
import net.corda.behave.node.configuration.DatabaseConfiguration

class PostgresConfigurationTemplate : DatabaseConfigurationTemplate() {

    override val connectionString: (DatabaseConfiguration) -> String
        get() = { "jdbc:postgresql://${it.host}:${it.port}/${it.database}" }

    override val config: (DatabaseConfiguration) -> String
        get() = {
            """
            |dataSourceProperties = {
            |    dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
            |    dataSource.url = "${connectionString(it)}"
            |    dataSource.user = "${it.username}"
            |    dataSource.password = "${it.password}"
            |}
            |database = {
            |    transactionIsolationLevel = READ_COMMITTED
            |}
            """
        }
}
package net.corda.behave.database.configuration

import net.corda.behave.database.DatabaseConfigurationTemplate
import net.corda.behave.node.configuration.DatabaseConfiguration

class SqlServerConfigurationTemplate : DatabaseConfigurationTemplate() {

    override val connectionString: (DatabaseConfiguration) -> String
        get() = { "jdbc:sqlserver://${it.host}:${it.port};database=${it.database}" }

    override val config: (DatabaseConfiguration) -> String
        get() = {
            """
            |dataSourceProperties = {
            |    dataSourceClassName = "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
            |    dataSource.url = "${connectionString(it)}"
            |    dataSource.user = "${it.username}"
            |    dataSource.password = "${it.password}"
            |}
            |database = {
            |    transactionIsolationLevel = READ_COMMITTED
            |    schema="${it.schema}"
            |}
            """
        }

}
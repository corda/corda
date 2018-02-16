/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
            |    initialiseSchema=true
            |    transactionIsolationLevel = READ_COMMITTED
            |    schema="${it.schema}"
            |}
            """
        }

}
/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.node.configuration

import net.corda.behave.database.DatabaseType

data class DatabaseConfiguration(
        val type: DatabaseType,
        val host: String,
        val port: Int,
        val username: String = type.settings.userName,
        val password: String,
        val database: String = type.settings.databaseName,
        val schema: String = type.settings.schemaName
) {

    fun config() = type.settings.config(this)
}

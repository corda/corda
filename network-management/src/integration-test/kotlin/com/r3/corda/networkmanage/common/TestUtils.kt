/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.SecureHash
import net.corda.testing.database.DatabaseConstants

const val HOST = "localhost"

const val DOORMAN_DB_NAME = "doorman"

fun networkMapInMemoryH2DataSourceConfig(nodeName: String? = null, postfix: String? = null) : Config {
    val nodeName = nodeName ?: SecureHash.randomSHA256().toString()
    val h2InstanceName = if (postfix != null) nodeName + "_" + postfix else nodeName

    return ConfigFactory.parseMap(mapOf(
            DatabaseConstants.DATA_SOURCE_CLASSNAME to "org.h2.jdbcx.JdbcDataSource",
            DatabaseConstants.DATA_SOURCE_URL to "jdbc:h2:mem:${h2InstanceName};DB_CLOSE_DELAY=-1",
            DatabaseConstants.DATA_SOURCE_USER to "sa",
            DatabaseConstants.DATA_SOURCE_PASSWORD to ""))
}
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

import java.util.*

const val HOST = "localhost"

fun makeTestDataSourceProperties(dbName: String): Properties {
    val props = Properties()
    props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    props.setProperty("dataSource.url", "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1")
    props.setProperty("dataSource.user", "sa")
    props.setProperty("dataSource.password", "")
    return props
}
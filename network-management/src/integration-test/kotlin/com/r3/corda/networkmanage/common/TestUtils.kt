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
package net.corda.testing.database

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.testing.database.DataSourceProperties.CONFIG_ENTRY
import net.corda.testing.database.DataSourceProperties.DATA_SOURCE_CLASSNAME
import net.corda.testing.database.DataSourceProperties.DATA_SOURCE_PASSWORD
import net.corda.testing.database.DataSourceProperties.DATA_SOURCE_URL
import net.corda.testing.database.DataSourceProperties.DATA_SOURCE_USER
import java.util.*

internal object DataSourceProperties {
    /** Entry name in [Config] containing datasource properties. */
    const val CONFIG_ENTRY = "dataSourceProperties."
    const val DATA_SOURCE_URL = "dataSource.url"
    const val DATA_SOURCE_CLASSNAME = "dataSourceClassName"
    const val DATA_SOURCE_USER = "dataSource.user"
    const val DATA_SOURCE_PASSWORD = "dataSource.password"
}

/**
 * Retrieves JDBC Data Source properties for HikariConfig from dataSourceProperties [Config] entry.
 * Use when [Config] is created manually without use of auto binding by NodeConfiguration.
 */
fun Config.toDataSourceProperties(): Properties {
    val props = Properties()
    props.setProperty(DATA_SOURCE_CLASSNAME, this.getString(CONFIG_ENTRY + DATA_SOURCE_CLASSNAME))
    props.setProperty(DATA_SOURCE_URL, this.getString(CONFIG_ENTRY + DATA_SOURCE_URL))
    props.setProperty(DATA_SOURCE_USER, this.getString(CONFIG_ENTRY + DATA_SOURCE_USER))
    props.setProperty(DATA_SOURCE_PASSWORD, this.getString(CONFIG_ENTRY + DATA_SOURCE_PASSWORD))
    return props
}

/**
 * Creates a dataSourceProperties entry of [Config].
 * @param url JDBC connection string, value for dataSource.url key
 * @param driverClass JDBC Driver class name, value for dataSource.url key
 * @param user database login, value for dataSource.user key
 * @param password database password, value for dataSource.password key
 */
fun dataSourceConfig(url: String, driverClass: String, user: String, password: String) : Config {
    return ConfigFactory.parseMap(mapOf(
            CONFIG_ENTRY + DATA_SOURCE_URL to url,
            CONFIG_ENTRY + DATA_SOURCE_CLASSNAME to driverClass,
            CONFIG_ENTRY + DATA_SOURCE_USER to user,
            CONFIG_ENTRY + DATA_SOURCE_PASSWORD to password))
}
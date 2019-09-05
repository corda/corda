package net.corda.notary.standalonejpa

import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.containers.wait.strategy.WaitStrategy
import java.util.*


interface DataSourceFactory {
    /** We have to delay container startup until we are ready to run a batch of tests, because otherwise multiple docker containers will be created at once, slowing testing down.*/
    fun startup()
    fun getDataSourceProperties(): Properties
    fun getJdbcUrl(): String
    fun close()
    val dockerImageName: String
}

/**
Password with complexity required by SQLServer
 */
const val strongPassword = "1Secure*Password1"

class SqlServerJpaContainer(dockerImageName: String?) : MSSQLServerContainer<SqlServerJpaContainer>(dockerImageName)
class SQLServerDataSourceFactory(override val dockerImageName: String) : DataSourceFactory {

    val container: SqlServerJpaContainer = SqlServerJpaContainer(dockerImageName)
            //.withInitScript("net/corda/notary/standalonejpa/sqlserversetup.sql")
            .withPassword(strongPassword)

    override fun startup() {
        try {
            container.start()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun getJdbcUrl(): String {
        return container.jdbcUrl
    }

    override fun getDataSourceProperties(): Properties {
        return Properties().also {
            it.setProperty("autoCommit", "false")
            //it.setProperty("catalog", "corda")
            it.setProperty("dataSource.url", getJdbcUrl())
            it.setProperty("jdbcUrl", getJdbcUrl())
            it.setProperty("username", "sa")
            it.setProperty("password", strongPassword)
        }
    }

    override fun close() {
        container.stop()
    }
}

class PostgreSqlJpaContainer(dockerImageName: String?) : PostgreSQLContainer<PostgreSqlJpaContainer>(dockerImageName)
class PostgreSQLDataSourceFactory(override val dockerImageName: String) : DataSourceFactory {

    val container = PostgreSqlJpaContainer(dockerImageName)
            .withUsername("corda")
            .withPassword(strongPassword)
            .withDatabaseName("corda")

    override fun startup() {
        try {
            container.start()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun getJdbcUrl(): String {
        return container.jdbcUrl
    }

    override fun getDataSourceProperties(): Properties {
        return Properties().also {
            it.setProperty("autoCommit", "false")
            it.setProperty("catalog", "corda")
            it.setProperty("dataSource.url", getJdbcUrl())
            it.setProperty("jdbcUrl", getJdbcUrl())
            it.setProperty("username", "corda")
            it.setProperty("password", strongPassword)
        }
    }

    override fun close() {
        container.stop()
    }
}

class CockroachDBJpaContainer(dockerImageName: String) : JdbcDatabaseContainer<CockroachDBJpaContainer>(dockerImageName) {
    override fun getPassword(): String {
        return ""
    }

    override fun getUsername(): String {
        return "root"
    }

    override fun getDriverClassName(): String {
        return "org.postgresql.Driver"
    }

    override fun getJdbcUrl(): String {
        return "jdbc:postgresql://127.0.0.1:${getMappedPort(26257)}/$databaseName?sslmode=disable"
    }

    override fun getTestQueryString(): String {
        return "SELECT 1;"
    }

    override fun getDatabaseName(): String {
        return "corda"
    }

    override fun getWaitStrategy(): WaitStrategy {
        return HttpWaitStrategy().forPort(getMappedPort(8080)).forPath("/health").forStatusCode(200)
    }
}

class CockroachDBDataSourceFactory(override val dockerImageName: String) : DataSourceFactory {

    val container = CockroachDBJpaContainer(dockerImageName)
            .withInitScript("net/corda/notary/standalonejpa/cockroachdbsetup.sql")
            .withExposedPorts(8080, 26257)
            .withCommand("start --insecure")

    override fun startup() {
        try {
            container.start()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun getJdbcUrl(): String {
        return container.jdbcUrl
    }

    override fun getDataSourceProperties(): Properties {
        return Properties().also {
            it.setProperty("autoCommit", "false")
            it.setProperty("catalog", "corda")
            it.setProperty("dataSource.url", getJdbcUrl())
            it.setProperty("jdbcUrl", getJdbcUrl())
            it.setProperty("username", "root")
            it.setProperty("password", "")
        }
    }

    override fun close() {
        container.stop()
    }
}

class MySqlJpaContainer(dockerImageName: String?) : MySQLContainer<MySqlJpaContainer>(dockerImageName)
class MySQLDataSourceFactory(override val dockerImageName: String) : DataSourceFactory {

    val container: MySqlJpaContainer = MySqlJpaContainer(dockerImageName)
            .withUsername("corda")
            .withPassword(strongPassword)
            .withDatabaseName("corda")
            .withInitScript("net/corda/notary/standalonejpa/mysqlsetup.sql")
            .withExposedPorts(3306, 33060)

    override fun startup() {
        try {
            container.start()
        } catch (e: Exception) {
            var logs = container.logs
            throw e
        }
    }

    override fun getJdbcUrl(): String {
        return container.jdbcUrl
    }

    override fun getDataSourceProperties(): Properties {
        return Properties().also {
            it.setProperty("autoCommit", "false")
            it.setProperty("dataSourceClassName", "com.mysql.cj.jdbc.MysqlDataSource")
            it.setProperty("catalog", "corda")
            it.setProperty("dataSource.url", getJdbcUrl())
            it.setProperty("username", "corda")
            it.setProperty("password", strongPassword)
        }
    }

    override fun close() {
        container.stop()
    }
}

class MariaDBJpaContainer(dockerImageName: String?) : MariaDBContainer<MariaDBJpaContainer>(dockerImageName)
class MariaDBDataSourceFactory(override val dockerImageName: String) : DataSourceFactory {

    val container: MariaDBJpaContainer = MariaDBJpaContainer(dockerImageName)
            .withUsername("corda")
            .withPassword(strongPassword)
            .withDatabaseName("corda")
            .withInitScript("net/corda/notary/standalonejpa/mysqlsetup.sql")

    override fun startup() {
        try {
            container.start()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun getJdbcUrl(): String {
        return container.jdbcUrl
    }

    override fun getDataSourceProperties(): Properties {
        return Properties().also {
            it.setProperty("autoCommit", "false")
            it.setProperty("catalog", "corda")
            it.setProperty("dataSource.url", getJdbcUrl())
            it.setProperty("jdbcUrl", getJdbcUrl())
            it.setProperty("username", "corda")
            it.setProperty("password", strongPassword)
        }
    }

    override fun close() {
        container.stop()
    }
}

class OracleJpaContainer(dockerImageName: String) : JdbcDatabaseContainer<OracleJpaContainer>(dockerImageName) {
    override fun getPassword(): String {
        return "oracle"
    }

    override fun getUsername(): String {
        return "system"
    }

    override fun getDriverClassName(): String {
        return "oracle.jdbc.driver.OracleDriver"
    }

    override fun getJdbcUrl(): String {
        return "jdbc:oracle:thin:@127.0.0.1:${getMappedPort(1521)}:XE"
    }

    override fun getTestQueryString(): String {
        return "SELECT * FROM DUAL"
    }
}

class OracleDataSourceFactory(override val dockerImageName: String) : DataSourceFactory {

    val container: OracleJpaContainer = OracleJpaContainer(dockerImageName)
            .withExposedPorts(1521)

    override fun startup() {
        try {
            container.start()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun getJdbcUrl(): String {
        return container.jdbcUrl
    }

    override fun getDataSourceProperties(): Properties {
        return Properties().also {
            it.setProperty("autoCommit", "false")
            it.setProperty("catalog", "system")
            it.setProperty("dataSource.url", getJdbcUrl())
            it.setProperty("jdbcUrl", getJdbcUrl())
            it.setProperty("username", "system")
            it.setProperty("password", "oracle")
        }
    }

    override fun close() {
        container.stop()
    }
}
package net.corda.testing.database

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.utilities.loggerFor
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

object DbScriptRunner {
    private val log = loggerFor<DbScriptRunner>()

    // System properties set in main 'corda-project' build.gradle
    private const val TEST_DB_ADMIN_USER = "test.db.admin.user"
    private const val TEST_DB_ADMIN_PASSWORD = "test.db.admin.password"

    fun runDbScript(dbProvider: String, initScript: String) {
        val parseOptions = ConfigParseOptions.defaults()
        val databaseConfig = ConfigFactory.parseResources("$dbProvider.conf", parseOptions.setAllowMissing(false))
        val dataSource = DriverManagerDataSource()
        dataSource.setDriverClassName(databaseConfig.getString("dataSourceProperties.dataSourceClassName"))
        dataSource.url = databaseConfig.getString("dataSourceProperties.dataSource.url")
        dataSource.username = System.getProperty(TEST_DB_ADMIN_USER)
        dataSource.password = System.getProperty(TEST_DB_ADMIN_PASSWORD)
        val initSchema = ClassPathResource(initScript )
        if (initSchema.exists()) {
            val databasePopulator = ResourceDatabasePopulator(false, true, null, initSchema)
            DatabasePopulatorUtils.execute(databasePopulator, dataSource)
        }
        else log.warn("DB Script missing: $initSchema")
    }
}

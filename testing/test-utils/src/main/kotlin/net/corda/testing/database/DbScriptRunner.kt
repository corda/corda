package net.corda.testing.database

import com.typesafe.config.ConfigFactory
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.ConfigHelper
import net.corda.testing.database.DatabaseConstants.DATA_SOURCE_CLASSNAME
import net.corda.testing.database.DatabaseConstants.DATA_SOURCE_PASSWORD
import net.corda.testing.database.DatabaseConstants.DATA_SOURCE_URL
import net.corda.testing.database.DatabaseConstants.DATA_SOURCE_USER
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.*
import org.springframework.util.StringUtils
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLWarning

object DbScriptRunner {
    private val logger = loggerFor<DbScriptRunner>()

    // System properties set in main 'corda-project' build.gradle
    private const val TEST_DB_ADMIN_USER = "test.db.admin.user"
    private const val TEST_DB_ADMIN_PASSWORD = "test.db.admin.password"

    private fun createDataSource(dbProvider: String): DriverManagerDataSource {

        val cordaConfigs = ConfigFactory.parseMap(System.getProperties().filterKeys { (it as String).startsWith(ConfigHelper.CORDA_PROPERTY_PREFIX) }
                .mapKeys { (it.key as String).removePrefix(ConfigHelper.CORDA_PROPERTY_PREFIX) }
                .filterKeys { listOf(DATA_SOURCE_URL, DATA_SOURCE_CLASSNAME, DATA_SOURCE_USER, DATA_SOURCE_PASSWORD).contains(it) })

        val testConfigs = ConfigFactory.parseMap(System.getProperties().filterKeys { listOf(TEST_DB_ADMIN_USER, TEST_DB_ADMIN_PASSWORD).contains(it) }
                .mapKeys { (it.key as String) })

        val databaseConfig = cordaConfigs
                .withFallback(testConfigs)
                .withFallback(ConfigFactory.parseResources("$dbProvider.conf"))

        return (DriverManagerDataSource()).also {
            it.setDriverClassName(databaseConfig.getString(DATA_SOURCE_CLASSNAME))
            it.url = databaseConfig.getString(DATA_SOURCE_URL)
            it.username = databaseConfig.getString(TEST_DB_ADMIN_USER)
            it.password = databaseConfig.getString(TEST_DB_ADMIN_PASSWORD)
        }
    }

    fun runDbScript(dbProvider: String, initScript: String? = null, databaseSchemas: List<String> = emptyList()) {
        if (initScript != null) {
            val initSchema = ClassPathResource(initScript)
            if (initSchema.exists()) {
                val encodedResource = EncodedResource(initSchema)
                val inputString = encodedResource.inputStream.bufferedReader().use { it.readText().split("\n") }
                val resolvedScripts = merge(inputString, databaseSchemas)
                logger.info("Executing DB Script for schemas $databaseSchemas with ${resolvedScripts.size} statements.")
                DatabasePopulatorUtils.execute(ListPopulator(false, true, resolvedScripts),
                        createDataSource(dbProvider))
            } else logger.warn("DB Script missing: $initSchema")
        }
    }

    fun merge(scripts: List<String>, schema: String): List<String> =
            scripts.map { it.replace("\${schema}", schema) }

    fun merge(scripts: List<String>, schemas: List<String>): List<String> =
            if (schemas.isEmpty()) scripts else schemas.map { merge(scripts, it) }.flatten()
}

//rewritten version of org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
class ListPopulator(private val continueOnError: Boolean,
                    private val ignoreFailedDrops: Boolean,
                    private val statements: List<String>) : DatabasePopulator {
    private val logger = loggerFor<DbScriptRunner>()
    override fun populate(connection: Connection) {
        try {
            logger.info("Executing SQL script")
            val startTime = System.currentTimeMillis()
            var stmtNumber = 0
            val stmt = connection.createStatement()
            try {
                for (statement in statements) {
                    stmtNumber++
                    try {
                        stmt.execute(statement)
                        val rowsAffected = stmt.updateCount
                        if (logger.isDebugEnabled) {
                            logger.debug(rowsAffected.toString() + " returned as update count for SQL: " + statement)
                            var warningToLog: SQLWarning? = stmt.warnings
                            while (warningToLog != null) {
                                logger.debug("SQLWarning ignored: SQL state '" + warningToLog.sqlState +
                                        "', error code '" + warningToLog.errorCode +
                                        "', message [" + warningToLog.message + "]")
                                warningToLog = warningToLog.nextWarning
                            }
                        }
                    } catch (ex: SQLException) {
                        val dropStatement = StringUtils.startsWithIgnoreCase(statement.trim { it <= ' ' }, "drop")
                        if ((continueOnError || dropStatement && ignoreFailedDrops)) {
                            val dropUserStatement = StringUtils.startsWithIgnoreCase(statement.trim { it <= ' ' }, "drop user ")
                            if (dropUserStatement) { // log to help spotting a node still logged on database after test has finished (happens on Oracle db)
                                logger.warn("SQLException for $statement: SQL state '" + ex.sqlState +
                                        "', error code '" + ex.errorCode +
                                        "', message [" + ex.message + "]")
                            } else {
                                logger.debug("SQLException for $statement", ex)
                            }
                        } else {
                            throw ex
                        }
                    }
                }
            } finally {
                try {
                    stmt.close()
                } catch (ex: Throwable) {
                    logger.debug("Could not close JDBC Statement", ex)
                }
            }
            val elapsedTime = System.currentTimeMillis() - startTime
            val resource = if (statements.isNotEmpty()) statements[0] + " [...]" else ""
            logger.info("Executed ${statements.size} SQL statements ($resource) in $elapsedTime ms.")
        } catch (ex: Exception) {
            if (ex is ScriptException) {
                throw ex
            }
            throw UncategorizedScriptException(
                    "Failed to execute database script from resource [resource]", ex)
        }
    }
}

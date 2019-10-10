package net.corda.node.persistence

import net.corda.core.internal.div
import net.corda.core.utilities.getOrThrow
import net.corda.node.flows.isQuasarAgentSpecified
import net.corda.node.logging.logFile
import net.corda.nodeapi.internal.persistence.DatabaseIncompatibleException
import net.corda.nodeapi.internal.persistence.SchemaMigrationError
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.ListenProcessDeathException
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DbSchemaInitialisationTest {
    @Test
    fun `database is initialised`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = isQuasarAgentSpecified())) {
            val nodeHandle = {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "true"))).getOrThrow()
            }()
            assertNotNull(nodeHandle)
        }
    }

    @Test
    fun `database is not initialised`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = isQuasarAgentSpecified())) {
            assertFailsWith(DatabaseIncompatibleException::class) {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "false")), nodeName).getOrThrow()
            }
        }
    }

    private val sampleChangeSetFileName = "migration/vault-schema.changelog-init.xml"
    private val sampleChangeSet = "$sampleChangeSetFileName::1511451595465-22::R3.Corda"
    private val loggerPrefix = "DatabaseInitialisation\\(id=\"[a-zA-Z0-9]{8}\";"
    private val nodeName = ALICE_NAME

    private fun Path.logFile(): File =
            (this / "logs").toFile().walk().filter { it.name.startsWith("node-") && it.extension == "log" }.single()

    private fun sqlSchema(schema: String? = null) = when {
        (schema.isNullOrEmpty()) -> "PUBLIC"
        else -> "\"$schema\""
    }

    @Test
    fun `database intilisation logger reports progress`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false)) {
            val logFile = {
                val nodeHandle = startNode(providedName = nodeName).getOrThrow()
                nodeHandle.stop()
                nodeHandle.logFile()
            }()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val loggerPrefixRegex = loggerPrefix.toRegex()
            val logs = logFile.useLines { lines ->
                lines.filter { it.contains(loggerPrefixRegex) }.toList()
            }
            Assertions.assertThat(logs).isNotEmpty

            val migrationStart = "${loggerPrefix}status=\"start\"\\)".toRegex()
            val migrationCount = "${loggerPrefix}change_set_count=\"[0-9]+\"\\)".toRegex()
            val migrationSuccessful = "${loggerPrefix}status=\"successful\"".toRegex()
            val sampleChangeSetToBeRun = "${loggerPrefix}changeset=\"$sampleChangeSet\";cordapp=\"none\";status=\"to be run\"\\)".toRegex()
            val sampleChangeSetStarted = "${loggerPrefix}changeset=\"$sampleChangeSet\";cordapp=\"none\";status=\"started\"\\)".toRegex()
            val sampleChangeSetSuccessful =
                    "${loggerPrefix}changeset=\"$sampleChangeSet\";cordapp=\"none\";status=\"successful\"\\)".toRegex()

            Assertions.assertThat(logs.filter { it.contains(migrationStart) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(migrationCount) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(migrationSuccessful) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(sampleChangeSetToBeRun) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(sampleChangeSetStarted) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(sampleChangeSetSuccessful) }).hasSize(1)
        }
    }

    @Test
    fun `enabled database intilisation logger reports error`() {
        val dbSettings = Properties().apply {
            setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
            setProperty("dataSource.user", "sa")
            setProperty("dataSource.password", "")
        }

        val schema = sqlSchema()

        driver(DriverParameters(notarySpecs = emptyList(), inMemoryDB = false, startNodesInProcess = false)) {
            val logFile = {
                val nodeHandle = startNode(providedName = nodeName).getOrThrow()
                nodeHandle.stop()
                nodeHandle.logFile()
            }()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val sampleChangeSetError = ("${loggerPrefix}changeset=\"$sampleChangeSet\";cordapp=\"none\";status=\"error\";error_code=\"" +
                    "${SchemaMigrationError.INCOMPATIBLE_CHANGE_SET.code}\";message=\"").toRegex()

            var errorLines = logFile.useLines { lines ->
                lines.filter { it.contains(sampleChangeSetError) }.count()
            }
            Assertions.assertThat(errorLines).isZero()

            val sql = "delete from $schema.databasechangelog where filename = '$sampleChangeSetFileName'"

            val url = "jdbc:h2:file:${baseDirectory(nodeName) / "persistence"}"

            DriverManager.getConnection(url, dbSettings["dataSource.user"] as String, dbSettings["dataSource.password"] as String).use {
                it.createStatement().execute(sql)
            }

            assertFailsWith(ListenProcessDeathException::class) { startNode(providedName = nodeName).getOrThrow() }

            errorLines = logFile.useLines { lines ->
                lines.filter { it.contains(sampleChangeSetError) }.count()
            }
            Assertions.assertThat(errorLines).isOne()
        }
    }

    @Test // test error code 2
    fun `missing database driver`() {
        driver(DriverParameters(notarySpecs = emptyList(), inMemoryDB = false, startNodesInProcess = false)) {
            assertFailsWith(ListenProcessDeathException::class) {
                startNode(providedName = nodeName,
                        customOverrides = mapOf("dataSourceProperties.dataSourceClassName" to "org.my.jdbc.Simon")).getOrThrow()
            }

            val logFile = this.baseDirectory(nodeName).logFile()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val sampleChangeSetError = ("${loggerPrefix}status=\"error\";error_code=\"${SchemaMigrationError.MISSING_DRIVER.code}\"" +
                    ";message=\"Could not find the database driver class.").toRegex()

            var errorLines = logFile.useLines { lines -> lines.filter { it.contains(sampleChangeSetError) }.count() }

            Assertions.assertThat(errorLines).isOne()
        }
    }

    @Test // test error code 3
    fun `invalid data source property`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false)) {
            val wrongPropertyName = "dataSourceClassNamee"
            val wrongProperty = "dataSourceProperties.$wrongPropertyName"
            assertFailsWith(ListenProcessDeathException::class) {
                startNode(providedName = nodeName,
                        customOverrides = mapOf(wrongProperty to "org.h2.jdbcx.JdbcDataSource")).getOrThrow()
            }

            val logFile = this.baseDirectory(nodeName).logFile()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val sampleChangeSetError = ("${loggerPrefix}status=\"error\";error_code=" +
                    "\"${SchemaMigrationError.INVALID_DATA_SOURCE_PROPERTY.code}\";message=" +
                    "\"Could not create the DataSource: Property $wrongPropertyName does not exist on target class").toRegex()

            var errorLines = logFile.useLines { lines -> lines.filter { it.contains(sampleChangeSetError) }.count() }

            Assertions.assertThat(errorLines).isOne()
        }
    }

    @Test //test code 9
    fun `core table created without Liquibase`() {
        val dbSettings = Properties().apply {
            setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
            setProperty("dataSource.user", "sa")
            setProperty("dataSource.password", "")
        }
        val schema = sqlSchema()

        driver(DriverParameters(notarySpecs = emptyList(), inMemoryDB = false, startNodesInProcess = false)) {
            val sql = "CREATE TABLE $schema.NODE_MESSAGE_IDS (" +
                    "MESSAGE_ID VARCHAR(64) NOT NULL, " +
                    "INSERTION_TIME TIMESTAMP NOT NULL, " +
                    "SENDER VARCHAR(64), " +
                    "SEQUENCE_NUMBER BIGINT, " +
                    "CONSTRAINT NODE_MESSAGE_IDS_PKEY PRIMARY KEY (MESSAGE_ID) )"
            val url = "jdbc:h2:file:${baseDirectory(nodeName) / "persistence"}"

            DriverManager.getConnection(url, dbSettings["dataSource.user"] as String, dbSettings["dataSource.password"] as String).use {
                it.createStatement().execute(sql)
            }
            assertFailsWith(ListenProcessDeathException::class) { startNode(providedName = nodeName).getOrThrow() }

            val logFile = this.baseDirectory(nodeName).logFile()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val changeSet = "migration/vault-schema.changelog-v5.xml::add_relevancy_status_column::R3.Corda"

            val sampleChangeSetError = ("${loggerPrefix}changeset=\"$changeSet\";cordapp=\"none\";status=\"error\";error_code=\"" +
                    "${SchemaMigrationError.INCOMPATIBLE_CHANGE_SET.code}\";message=\"Migration failed for change set $changeSet").toRegex()
            val migrationError = ("${loggerPrefix}status=\"error\";error_code=\"${SchemaMigrationError.INCOMPATIBLE_CHANGE_SET.code}" +
                    "\";message=\"Could not create the DataSource: Migration failed for change set $changeSet").toRegex()

            var errorLines = logFile.useLines { lines -> lines.filter { it.contains(sampleChangeSetError) }.count() }
            Assertions.assertThat(errorLines).isOne()

            errorLines = logFile.useLines { lines -> lines.filter { it.contains(migrationError) }.count() }
            Assertions.assertThat(errorLines).isOne()
        }
    }
}
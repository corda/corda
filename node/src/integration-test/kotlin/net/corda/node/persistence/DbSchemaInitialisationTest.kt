package net.corda.node.persistence

import net.corda.core.internal.div
import net.corda.core.utilities.getOrThrow
import net.corda.node.flows.isQuasarAgentSpecified
import net.corda.node.logging.logFile
import net.corda.nodeapi.internal.persistence.DatabaseIncompatibleException
import net.corda.nodeapi.internal.persistence.SchemaMigrationError
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.ListenProcessDeathException
import net.corda.testing.node.internal.cordappsForPackages
import net.test.cordapp.schemaInitialisation.SchemaA
import org.assertj.core.api.Assertions
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
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
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "false"))).getOrThrow()
            }
        }
    }

    val sampleChangesetFileName = "migration/vault-schema.changelog-init.xml"
    val sampleChangeset = "$sampleChangesetFileName::1511451595465-22::R3.Corda"
    val loggerPrefix = "DatabaseInitialisation\\(id=[a-zA-Z0-9]{8};"

    @Test
    fun `database intilisation logger reports progress`() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing.
        // More investigation is needed to establish why.
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false)) {
            val logFile = {
                val nodeHandle = startNode(providedName = ALICE_NAME).getOrThrow()
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
            val sampleChangeSetToBeRun = "${loggerPrefix}changeset=\"$sampleChangeset\";status=\"to be run\"\\)".toRegex()
            val sampleChangeSetStarted = "${loggerPrefix}changeset=\"$sampleChangeset\";status=\"started\"\\)".toRegex()
            val sampleChangeSetSuccessful = "${loggerPrefix}changeset=\"$sampleChangeset\";status=\"successful\"\\)".toRegex()

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
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing.
        // More investigation is needed to establish why.
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        driver(DriverParameters(notarySpecs = emptyList(), inMemoryDB = false, startNodesInProcess = false)) {
            val (nodeName, logFile) = {
                val nodeHandle = startNode(providedName = ALICE_NAME).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                nodeHandle.stop()
                Pair(nodeName, nodeHandle.logFile())
            }()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val sampleChangeSetError = "${loggerPrefix}changeset=\"$sampleChangeset\";status=\"error\";error_code=\"${SchemaMigrationError.INCOMPATIBLE_CHANGE_SET.code}\";message=\"".toRegex()

            var errorLines = logFile.useLines {
                lines -> lines.filter { it.contains(sampleChangeSetError) }.count()
            }
            Assertions.assertThat(errorLines).isZero()

            DriverManager.getConnection("jdbc:h2:file:${baseDirectory(nodeName) / "persistence"}", "sa", "").use {
                it.createStatement().execute("delete from databasechangelog where filename = '$sampleChangesetFileName'")
            }

            assertFailsWith(ListenProcessDeathException::class) { startNode(providedName = ALICE_NAME).getOrThrow() }

            errorLines = logFile.useLines {
                lines -> lines.filter { it.contains(sampleChangeSetError) }.count()
            }

            Assertions.assertThat(errorLines).isOne()
        }
    }

    private fun Path.logFile(): File = (this / "logs").toFile().walk().filter { it.name.startsWith("node-") && it.extension == "log" }.single()

    @Test
    fun wrongDriver() {
        driver(DriverParameters(notarySpecs = emptyList(), inMemoryDB = false, startNodesInProcess = false)) {
            val nodeName = ALICE_NAME
            assertFailsWith(ListenProcessDeathException::class) {
                startNode(providedName = nodeName,
                        customOverrides = mapOf("dataSourceProperties.dataSourceClassName" to "org.my.jdbc.Simon")).getOrThrow()
            }

            val logFile = this.baseDirectory(nodeName).logFile()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val sampleChangeSetError = "${loggerPrefix}status=\"error\";error_code=\"${SchemaMigrationError.MISSING_DRIVER.code}\";message=\"Could not find the database driver class.".toRegex()

            var errorLines = logFile.useLines { lines -> lines.filter { it.contains(sampleChangeSetError) }.count() }

            Assertions.assertThat(errorLines).isOne()
        }
    }

    @Test
    fun wrongDataSourceProperty() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false)) {
            val nodeName = ALICE_NAME
            val wrongPropertyName = "dataSourceClassNamee"
            val wrongProperty = "dataSourceProperties.$wrongPropertyName"
            assertFailsWith(ListenProcessDeathException::class) {
                startNode(providedName = nodeName,
                        customOverrides = mapOf(wrongProperty to "org.h2.jdbcx.JdbcDataSource")).getOrThrow()
            }

            val logFile = this.baseDirectory(nodeName).logFile()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val sampleChangeSetError = "${loggerPrefix}status=\"error\";error_code=\"${SchemaMigrationError.INVALID_DATA_SOURCE_PROPERTY.code}\";message=\"Could not create the DataSource: Property $wrongPropertyName does not exist on target class".toRegex()

            var errorLines = logFile.useLines { lines -> lines.filter {
               println(it + " " + it.contains(sampleChangeSetError))
                it.contains(sampleChangeSetError)
            }.count()
            }

            Assertions.assertThat(errorLines).isOne()
        }
    }

    @Test
    fun manualInsert() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing.
        // More investigation is needed to establish why.
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        driver(DriverParameters(notarySpecs = emptyList(), inMemoryDB = false, startNodesInProcess = false)) {
            val nodeName = ALICE_NAME

            val sql = "CREATE TABLE PUBLIC.NODE_MESSAGE_IDS (\n" +
                    "\tMESSAGE_ID VARCHAR(64) NOT NULL,\n" +
                    "\tINSERTION_TIME TIMESTAMP NOT NULL,\n" +
                    "\tSENDER VARCHAR(64),\n" +
                    "\tSEQUENCE_NUMBER BIGINT,\n" +
                    "\tCONSTRAINT NODE_MESSAGE_IDS_PKEY PRIMARY KEY (MESSAGE_ID)\n" +
                    ");"
            DriverManager.getConnection("jdbc:h2:file:${baseDirectory(nodeName) / "persistence"}", "sa", "").use {
                it.createStatement().execute(sql)
            }

            assertFailsWith(ListenProcessDeathException::class) { startNode(providedName = nodeName).getOrThrow() }

            val logFile = this.baseDirectory(nodeName).logFile()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)
         //  DatabaseInitialisation(id=vCHWuciN;changeset="migration/vault-schema.changelog-v5.xml::add_relevancy_status_column::R3.Corda";status="error";error_code="9";message="Migration failed for change set migration/vault-schema.changelog-v5.xml::add_relevancy_status_column::R3.Corda:      Reason: liquibase.exception.DatabaseException: Table \"VAULT_STATES\" not found\; SQL statement: ALTER TABLE PUBLIC.vault_states ADD relevancy_status INT [42102-199] [Failed SQL: ALTER TABLE PUBLIC.vault_states ADD relevancy_status INT]")
         //  DatabaseInitialisation(id=vCHWuciN;status="error";error_code="9";message="Could not create the DataSource: Migration failed for change set migration/vault-schema.changelog-v5.xml::add_relevancy_status_column::R3.Corda:      Reason: liquibase.exception.DatabaseException: Table \"VAULT_STATES\" not found\; SQL statement: ALTER TABLE PUBLIC.vault_states ADD relevancy_status INT [42102-199] [Failed SQL: ALTER TABLE PUBLIC.vault_states ADD relevancy_status INT]")

            val sampleChangeSetError = "${loggerPrefix}1changeset=\"migration/vault-schema.changelog-v5.xml::add_relevancy_status_column::R3.Corda\";status=\"error\";error_code=\"${SchemaMigrationError.INCOMPATIBLE_CHANGE_SET.code}\";message=\"Migration failed for change set migration/vault-schema.changelog-v5.xml::add_relevancy_status_column::R3.Corda".toRegex()
            val migrationError = "${loggerPrefix}status=\"error\";error_code=\"${SchemaMigrationError.INCOMPATIBLE_CHANGE_SET.code}\";message=\"Could not create the DataSource: Migration failed for change set migration/vault-schema.changelog-v5.xml::add_relevancy_status_column::R3.Corda".toRegex()

            var errorLines = logFile.useLines { lines -> lines.filter { it.contains(sampleChangeSetError) }.count() }
            Assertions.assertThat(errorLines).isOne()

            errorLines = logFile.useLines { lines -> lines.filter { it.contains(migrationError) }.count() }
            Assertions.assertThat(errorLines).isOne()
        }
    }

    @Test
    fun cordappWithoutManagementScript() {
        val cordappPackage = (SchemaA::class.java.canonicalName as String).substringBeforeLast(".")
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false, cordappsForAllNodes = cordappsForPackages(cordappPackage))) {
            val nodeName = ALICE_NAME
            val nodeHandle = startNode(providedName = nodeName).getOrThrow()
            nodeHandle.stop()

            val logFile = this.baseDirectory(nodeName).logFile()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val sampleChangeSetError =
                    "${loggerPrefix}status=\"error\";error_code=\"".toRegex()
            var errorLines = logFile.useLines { lines -> lines.filter { it.contains(sampleChangeSetError) }.count() }

            Assertions.assertThat(errorLines).isZero()
        }
    }
}
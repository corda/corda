package net.corda.node.persistence

import net.corda.core.internal.div
import net.corda.core.utilities.getOrThrow
import net.corda.node.logging.logFile
import net.corda.nodeapi.internal.persistence.DatabaseIncompatibleException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.ListenProcessDeathException
import org.assertj.core.api.Assertions
import org.junit.Assume
import org.junit.Test
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

    val sampleChangeset = "migration/vault-schema.changelog-init.xml::1511451595465-22::R3.Corda"
    val prefix = "databaseInitialisation\\(id=[a-zA-Z0-9]{8};"

    @Test
    fun `database intilisation logger is disabled by default`() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing.
        // More investigation is needed to establish why.
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false
                // system property "formattedDatabaseInitialisationLogLevel" is not set
        )) {
            val logFile = {
                val nodeHandle = startNode(providedName = ALICE_NAME).getOrThrow()
                nodeHandle.stop()
                nodeHandle.logFile()
            }()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val logs = logFile.useLines { lines ->
                lines.filter { it.contains("databaseInitialisation(id=") }.toList()
            }
            Assertions.assertThat(logs).isEmpty()
        }
    }

    @Test
    fun `enabled database intilisation logger reports progress`() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing. More investigation is needed to establish why.
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = false,
                systemProperties = mapOf("formattedDatabaseInitialisationLogLevel" to "INFO")
        )) {
            val logFile = {
                val nodeHandle = startNode(providedName = ALICE_NAME).getOrThrow()
                nodeHandle.stop()
                nodeHandle.logFile()
            }()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val logs = logFile.useLines { lines ->
                lines.filter { it.contains("databaseInitialisation(id=") }.toList()
            }
            Assertions.assertThat(logs).isNotEmpty

            val migrationStart = "${prefix}status=\"start\"\\)".toRegex()
            val migrationCount = "${prefix}change_set_count=\"[0-9]+\"\\)".toRegex()
            val migrationSuccessful = "${prefix}status=\"successful\"".toRegex()
            val sampleChangeSetToBeRun = "${prefix}changeset=\"$sampleChangeset\";status=\"to be run\"\\)".toRegex()
            val sampleChangeSetStarted = "${prefix}changeset=\"$sampleChangeset\";status=\"started\"\\)".toRegex()
            val sampleChangeSetSuccessful = "${prefix}changeset=\"$sampleChangeset\";status=\"successful\"\\)".toRegex()

            Assertions.assertThat(logs.filter { it.contains(migrationStart) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(migrationCount) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(migrationSuccessful) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(sampleChangeSetToBeRun) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(sampleChangeSetStarted) }).hasSize(1)
            Assertions.assertThat(logs.filter { it.contains(sampleChangeSetSuccessful) }).hasSize(1)
        }
    }

    @Test
    fun `enabled database intilisation logger reports migration error`() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing. More investigation is needed to establish why.
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        driver(DriverParameters(notarySpecs = emptyList(), inMemoryDB = false, startNodesInProcess = false,
                systemProperties = mapOf("formattedDatabaseInitialisationLogLevel" to "INFO")
        )) {
            val (nodeName, logFile) = {
                val nodeHandle = startNode(providedName = ALICE_NAME).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                nodeHandle.stop()
                Pair(nodeName, nodeHandle.logFile())
            }()

            Assertions.assertThat(logFile.length()).isGreaterThan(0)

            val sampleChangeSetError = "${prefix}changeset=\"$sampleChangeset\";status=\"error\";message=\"".toRegex()

            var linesWithError = logFile.useLines { lines -> lines.filter { it.contains(sampleChangeSetError) }.toList() }
            Assertions.assertThat(linesWithError).isEmpty()

            DriverManager.getConnection("jdbc:h2:file:${baseDirectory(nodeName) / "persistence"}", "sa", "").use { connection ->
                connection.createStatement().execute("delete from databasechangelog where filename = 'migration/vault-schema.changelog-init.xml'")
            }

            assertFailsWith(ListenProcessDeathException::class) { startNode(providedName = ALICE_NAME).getOrThrow() }

            linesWithError = logFile.useLines { lines -> lines.filter { it.contains(sampleChangeSetError) }.toList() }

            Assertions.assertThat(linesWithError.size).isEqualTo(1)
        }
    }
}
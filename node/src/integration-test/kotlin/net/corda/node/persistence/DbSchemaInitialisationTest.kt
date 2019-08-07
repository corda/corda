package net.corda.node.persistence

import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.DatabaseIncompatibleException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DbSchemaInitialisationTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME, DUMMY_NOTARY_NAME)
    }

    @Test
    fun `database is initialised`() {
        // Enterprise does create H2 database only based on initialiseSchema flag, ignore the test against remote dbs
        Assume.assumeTrue(!isRemoteDatabaseMode())
        driver(DriverParameters(startNodesInProcess = isQuasarAgentSpecified())){
            val nodeHandle = {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "true",
                        // Enterprise - OS diff - an additional Enterprise only flag runMigration
                        "database.runMigration" to "false"))).getOrThrow()
            }()
            assertNotNull(nodeHandle)
        }
    }

    @Ignore // Ignoring, an in-memory H2 database is not properly closed in this test (when the node throws exception), and a db instance is reused by subsequent tests
    // FlowsDrainingModeContentionTest sets db to flow-draining mode, AdditionP2PAddressModeTest fails on this mode
    @Test
    fun `database is not initialised`() {
        driver(DriverParameters(startNodesInProcess = isQuasarAgentSpecified())){
            assertFailsWith(DatabaseIncompatibleException::class) {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "false",
                        "database.runMigration" to "false")), ALICE_NAME).getOrThrow()
            }
        }
    }

    // Enterprise only test  - when running against database other than H2, runMigration flag is be used
    @Test
    fun `remote database is not initialised as runMigration flag takes precedence`() {
        Assume.assumeTrue(isRemoteDatabaseMode())
        driver(DriverParameters(startNodesInProcess = isQuasarAgentSpecified())){
            assertFailsWith(DatabaseIncompatibleException::class) {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "true",
                        "database.runMigration" to "false")), ALICE_NAME).getOrThrow()
            }
        }
    }

    // Enterprise only test - when running against database other than H2, the runMigration flag is used
    @Test
    fun `remote database is initialised as runMigration flag takes precedence`() {
        // Enterprise does create remote database based on runMigration flag, so ignore the test against H2
        Assume.assumeTrue(isRemoteDatabaseMode())
        driver(DriverParameters(startNodesInProcess = isQuasarAgentSpecified())){
            val nodeHandle = {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "false",
                        "database.runMigration" to "true")), ALICE_NAME).getOrThrow()
            }()
            assertNotNull(nodeHandle)
        }
    }
}
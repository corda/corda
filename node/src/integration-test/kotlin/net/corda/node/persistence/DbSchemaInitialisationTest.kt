package net.corda.node.persistence

import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.DatabaseIncompatibleException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DbSchemaInitialisationTest {

    @Test
    fun `database is initialised`() {
        driver(DriverParameters(startNodesInProcess = isQuasarAgentSpecified())) {
            val nodeHandle = {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "true"))).getOrThrow()
            }()
            assertNotNull(nodeHandle)
        }
    }

    @Test
    fun `database is not initialised`() {
        driver(DriverParameters(startNodesInProcess = isQuasarAgentSpecified())) {
            assertFailsWith(DatabaseIncompatibleException::class) {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "false"))).getOrThrow()
            }
        }
    }
}
package net.corda.node.persistence

import net.corda.core.utilities.getOrThrow
import net.corda.node.flows.isQuasarAgentSpecified
import net.corda.node.internal.ConfigurationException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import org.junit.Test
import kotlin.test.assertFailsWith

class DbSchemaInitialisationTest {
    @Test(timeout = 300_000)
    fun `database initialisation not allowed in config`() {
        driver(DriverParameters(startNodesInProcess = isQuasarAgentSpecified(), cordappsForAllNodes = emptyList())) {
            assertFailsWith(ConfigurationException::class) {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "false"))).getOrThrow()
            }
        }
    }

}
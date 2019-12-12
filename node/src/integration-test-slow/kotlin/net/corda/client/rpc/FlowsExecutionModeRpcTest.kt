package net.corda.client.rpc

import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.User
import org.assertj.core.api.Assertions
import org.junit.Assume
import org.junit.Test

class FlowsExecutionModeRpcTest {

    @Test
    fun `persistent state survives node restart`() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing.
        // More investigation is needed to establish why.
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        val user = User("mark", "dadada", setOf(Permissions.invokeRpc("setFlowsDrainingModeEnabled"), Permissions.invokeRpc("isFlowsDrainingModeEnabled")))
        driver(DriverParameters(
                inMemoryDB = false,
                startNodesInProcess = true,
                notarySpecs = emptyList(),
                cordappsForAllNodes = emptyList()
        )) {
            val nodeName = {
                val nodeHandle = startNode(rpcUsers = listOf(user)).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.chooseIdentity().name
                nodeHandle.rpc.setFlowsDrainingModeEnabled(true)
                nodeHandle.stop()
                nodeName
            }()

            val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user)).getOrThrow()
            Assertions.assertThat(nodeHandle.rpc.isFlowsDrainingModeEnabled()).isEqualTo(true)
            nodeHandle.stop()
        }
    }
}
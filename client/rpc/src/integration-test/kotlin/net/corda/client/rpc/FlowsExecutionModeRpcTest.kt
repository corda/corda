package net.corda.client.rpc

import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.internal.packageName
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.services.Permissions
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.User
import net.corda.testing.node.internal.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

class FlowsExecutionModeRpcTest {

    @Test
    fun `persistent state survives node restart`() {
        // Temporary disable this test when executed on Windows. It is known to be sporadically failing.
        // More investigation is needed to establish why.
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"))

        val user = User("mark", "dadada", setOf(invokeRpc("setFlowsDrainingModeEnabled"), invokeRpc("isFlowsDrainingModeEnabled")))
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = true)) {
            val nodeName = {
                val nodeHandle = startNode(rpcUsers = listOf(user)).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.chooseIdentity().name
                nodeHandle.rpc.setFlowsDrainingModeEnabled(true)
                nodeHandle.stop()
                nodeName
            }()

            val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user)).getOrThrow()
            assertThat(nodeHandle.rpc.isFlowsDrainingModeEnabled()).isEqualTo(true)
            nodeHandle.stop()
        }
    }
}

class FlowsExecutionModeTests : NodeBasedTest(listOf("net.corda.finance.contracts", CashSchemaV1::class.packageName)) {

    private val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    private lateinit var node: StartedNode<Node>
    private lateinit var client: CordaRPCClient

    @Before
    fun setup() {
        node = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        client = CordaRPCClient(node.internals.configuration.rpcOptions.address)
    }

    @Test
    fun `flows draining mode can be enabled and queried`() {
        asALoggerUser { rpcOps ->
            val newValue = true
            rpcOps.setFlowsDrainingModeEnabled(true)

            val flowsExecutionMode = rpcOps.isFlowsDrainingModeEnabled()

            assertThat(flowsExecutionMode).isEqualTo(newValue)
        }
    }

    @Test
    fun `flows draining mode can be disabled and queried`() {
        asALoggerUser { rpcOps ->
            rpcOps.setFlowsDrainingModeEnabled(true)
            val newValue = false
            rpcOps.setFlowsDrainingModeEnabled(newValue)

            val flowsExecutionMode = rpcOps.isFlowsDrainingModeEnabled()

            assertThat(flowsExecutionMode).isEqualTo(newValue)
        }
    }

    @Test
    fun `node starts with flows draining mode disabled`() {
        asALoggerUser { rpcOps ->
            val defaultStartingMode = rpcOps.isFlowsDrainingModeEnabled()

            assertThat(defaultStartingMode).isEqualTo(false)
        }
    }

    private fun login(username: String, password: String, externalTrace: Trace? = null, impersonatedActor: Actor? = null): CordaRPCConnection {
        return client.start(username, password, externalTrace, impersonatedActor)
    }

    private fun asALoggerUser(action: (CordaRPCOps) -> Unit) {
        login(rpcUser.username, rpcUser.password).use {
            action(it.proxy)
        }
    }
}

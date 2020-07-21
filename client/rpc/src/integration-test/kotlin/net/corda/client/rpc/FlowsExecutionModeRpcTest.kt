package net.corda.client.rpc

import net.corda.core.context.Actor
import net.corda.core.context.Trace
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.internal.NodeWithInfo
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.User
import net.corda.testing.node.internal.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class FlowsExecutionModeTests : NodeBasedTest() {

    private val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
    private lateinit var node: NodeWithInfo
    private lateinit var client: CordaRPCClient

    @Before
    override fun setUp() {
        super.setUp()
        node = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        client = CordaRPCClient(node.node.configuration.rpcOptions.address)
    }

    @Test(timeout=300_000)
	fun `flows draining mode can be enabled and queried`() {
        asALoggerUser { rpcOps ->
            val newValue = true
            rpcOps.setFlowsDrainingModeEnabled(true)

            val flowsExecutionMode = rpcOps.isFlowsDrainingModeEnabled()

            assertThat(flowsExecutionMode).isEqualTo(newValue)
        }
    }

    @Test(timeout=300_000)
	fun `flows draining mode can be disabled and queried`() {
        asALoggerUser { rpcOps ->
            rpcOps.setFlowsDrainingModeEnabled(true)
            val newValue = false
            rpcOps.setFlowsDrainingModeEnabled(newValue)

            val flowsExecutionMode = rpcOps.isFlowsDrainingModeEnabled()

            assertThat(flowsExecutionMode).isEqualTo(newValue)
        }
    }

    @Test(timeout=300_000)
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

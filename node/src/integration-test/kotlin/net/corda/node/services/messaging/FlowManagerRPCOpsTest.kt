@file:Suppress("DEPRECATION")
package net.corda.node.services.messaging

import net.corda.client.rpc.ext.MultiRPCClient
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.isRegularFile
import net.corda.core.internal.list
import net.corda.core.messaging.flows.FlowManagerRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import kotlin.test.assertNotNull
import net.corda.core.internal.messaging.FlowManagerRPCOps as InternalFlowManagerRPCOps

class FlowManagerRPCOpsTest {

    @Test(timeout = 300_000)
    fun `net_corda_core_internal_messaging_FlowManagerRPCOps can be accessed using the MultiRPCClient`() {
        val user = User("user", "password", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            val client = MultiRPCClient(nodeAHandle.rpcAddress, InternalFlowManagerRPCOps::class.java, user.username, user.password)

            val logDirPath = nodeAHandle.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
            logDirPath.createDirectories()

            client.use {
                val rpcOps = it.start().getOrThrow(20.seconds).proxy
                rpcOps.dumpCheckpoints()
                it.stop()
            }

            assertNotNull(logDirPath.list().singleOrNull { it.isRegularFile() })
        }
    }

    @Test(timeout = 300_000)
    fun `net_corda_core_messaging_flows_FlowManagerRPCOps can be accessed using the MultiRPCClient`() {
        val user = User("user", "password", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            val client = MultiRPCClient(nodeAHandle.rpcAddress, FlowManagerRPCOps::class.java, user.username, user.password)

            val logDirPath = nodeAHandle.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
            logDirPath.createDirectories()

            client.use {
                val rpcOps = it.start().getOrThrow(20.seconds).proxy
                rpcOps.dumpCheckpoints()
                it.stop()
            }

            assertNotNull(logDirPath.list().singleOrNull { it.isRegularFile() })
        }
    }
}
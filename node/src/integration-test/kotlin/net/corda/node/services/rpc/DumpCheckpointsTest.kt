package net.corda.node.services.rpc

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DumpCheckpointsTest {

    @Test
    fun `Verify checkpoint dump via RPC`() {
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                val proxy = it.proxy as InternalCordaRPCOps
                assertFailsWith<CordaRuntimeException> {
                    proxy.startFlow(::GeneralExternalFailureFlow, nodeBHandle.nodeInfo.singleIdentity()).returnValue.getOrThrow()
                }
                assertEquals(0, GeneralExternalFailureFlow.retryCount)
                // 1 for the errored flow kept for observation and another for GetNumberOfCheckpointsFlow
                assertEquals(1, proxy.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())

                (nodeAHandle.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME).createDirectories()
                proxy.dumpCheckpoints()
            }
        }
    }
}

@StartableByRPC
@InitiatingFlow
class GeneralExternalFailureFlow(private val party: Party) : FlowLogic<Unit>() {
    companion object {
        // start negative due to where it is incremented
        var retryCount = -1
    }

    @Suspendable
    override fun call() {
        initiateFlow(party).send("hello there")
        // checkpoint will restart the flow after the send
        retryCount += 1
        throw IllegalStateException("Some user general exception")
    }
}

@InitiatedBy(GeneralExternalFailureFlow::class)
class GeneralExternalFailureResponder(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
    }
}

@StartableByRPC
class GetNumberOfCheckpointsFlow : FlowLogic<Int>() {
    override fun call(): Int {
        var count = 0
        serviceHub.jdbcSession().prepareStatement("select * from node_checkpoints").use { ps ->
            ps.executeQuery().use { rs ->
                while(rs.next()) {
                    count++
                }
            }
        }
        return count
    }
}
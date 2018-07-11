package net.corda.node.flows

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.packageName
import net.corda.core.internal.readLines
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.CheckpointIncompatibleException
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions
import net.corda.testMessage.Message
import net.corda.testMessage.MessageState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.RandomFree
import net.corda.testing.node.User
import net.test.cordapp.v1.SendMessageFlow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowCheckpointVersionNodeStartupCheck {
    companion object {
        val message = Message("Hello world!")
    }

    @Test
    fun `restart nodes with incompatible version of sunspended flow - different jar name`() {

        val user = User("mark", "dadada", setOf(Permissions.startFlow<SendMessageFlow>(), Permissions.invokeRpc("vaultQuery"), Permissions.invokeRpc("vaultTrack")))
        return driver(DriverParameters(isDebug = true,
                startNodesInProcess = false, // start nodes in separate processes to ensure CordappLoader is not shared between restarts
                inMemoryDB = false, // ensure database is persisted between node restarts so we can keep suspended flow in Bob's node
                portAllocation = RandomFree, extraCordappPackagesToScan = listOf(MessageState::class.packageName, "net.test.cordapp.v1"))) {
            val logFolder = {
                val alice = startNode(rpcUsers = listOf(user), providedName = ALICE_NAME).getOrThrow()
                val bob = startNode(rpcUsers = listOf(user), providedName = BOB_NAME).getOrThrow()
                alice.stop()
                CordaRPCClient(bob.rpcAddress).start(user.username, user.password).use {
                    val flowTracker = it.proxy.startTrackedFlow(::SendMessageFlow, message, defaultNotaryIdentity, alice.nodeInfo.singleIdentity()).progress
                    // wait until Bob progresses as far as possible because Alice node is offline
                    flowTracker.takeFirst { it == SendMessageFlow.Companion.FINALISING_TRANSACTION.label }.toBlocking().single()
                }
                val logFolder = bob.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
                // SendMessageFlow suspends in Bob node
                bob.stop()
                logFolder
            }()

            //after nodes restart the package with the flow is loaded bvia a JAR filw with different name (a random UUID is added)
            startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, customOverrides = mapOf("devMode" to false)).getOrThrow()
            assertFailsWith(net.corda.testing.node.internal.ListenProcessDeathException::class) {
                //ALic node is up so Bob's suspended flow should restart, however this time is loaded from different JAR file
                startNode(providedName = BOB_NAME, rpcUsers = listOf(user), customOverrides = mapOf("devMode" to false)).getOrThrow()
            }

            val logFile = logFolder.list { it.filter { it.fileName.toString().endsWith(".log") }.findAny().get() }
            val expectedLogMessage = CheckpointIncompatibleException.FlowNotInstalledException(SendMessageFlow::class.java)

            val numberOfNodesThatLogged = logFile.readLines { it.filter { expectedLogMessage.message in it }.count() }
            assertEquals(1, numberOfNodesThatLogged)
        }
    }
}
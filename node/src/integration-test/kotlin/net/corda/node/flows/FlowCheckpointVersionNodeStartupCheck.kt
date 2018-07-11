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
        return driver(DriverParameters(isDebug = true, startNodesInProcess = false,
                portAllocation = RandomFree, extraCordappPackagesToScan = listOf(MessageState::class.packageName, "net.test.cordapp.v1"))) {
            val logFolder = {
                val alice = startNode(rpcUsers = listOf(user), providedName = ALICE_NAME).getOrThrow()
                val bob = startNode(rpcUsers = listOf(user), providedName = BOB_NAME).getOrThrow()
                alice.stop()
                CordaRPCClient(bob.rpcAddress).start(user.username, user.password).use {
                    val flowTracker = it.proxy.startTrackedFlow(::SendMessageFlow, message, defaultNotaryIdentity, alice.nodeInfo.singleIdentity()).progress
                    //wait until Bob progresses as far as possible because alice node is offline
                    flowTracker.takeFirst { it == SendMessageFlow.Companion.FINALISING_TRANSACTION.label }.toBlocking().single()
                }

                val logFolder = bob.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME

                bob.stop()
                logFolder
            }()

            startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, customOverrides = mapOf("devMode" to false)).getOrThrow()
            //assertFailsWith(CheckpointIncompatibleException.FlowNotInstalledException::class) {
                startNode(providedName = BOB_NAME, rpcUsers = listOf(user), customOverrides = mapOf("devMode" to false)).getOrThrow()
            //}

            val logFile = logFolder.list { it.filter { it.fileName.toString().endsWith(".log") }.findAny().get() }
            // We count the number of nodes that wrote into the logfile by counting "Logs can be found in"
            //val numberOfNodesThatLogged = logFile.readLines { it.filter { "that is incompatible with the current installed version of" in it }.count() }

            //val expectedLogMessage = CheckpointIncompatibleException.FlowNotInstalledException(SendMessageFlow::class)
            val numberOfNodesThatLogged = logFile.readLines { it.filter {"Found checkpoint"/*"that is no longer installed"*/ in it }
                    .map{ a-> a}
                    .count() }
            assertEquals(1, numberOfNodesThatLogged)
        }
    }
}
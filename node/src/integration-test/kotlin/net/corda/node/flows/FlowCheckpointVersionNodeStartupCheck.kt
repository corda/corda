package net.corda.node.flows

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.packageName
import net.corda.core.internal.readLines
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
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
import net.test.cordapp.v1.SendMessageFlowY
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FlowCheckpointVersionNodeStartupCheck {
    companion object {
        val message = Message("Hello world!")
    }

    fun nodes_interaction(message: Message, cordappsVersionAtStartup: List<String>, cordappsVersionAtRestart: List<String>): StateAndRef<MessageState>? {

        val user = User("mark", "dadada", setOf(Permissions.startFlow<SendMessageFlowY>(), Permissions.invokeRpc("vaultQuery"), Permissions.invokeRpc("vaultTrack")))
        return driver(DriverParameters(isDebug = true, startNodesInProcess = false,//isQuasarAgentSpecified(),
                portAllocation = RandomFree, extraCordappPackagesToScan = listOf(MessageState::class.packageName))) {
            {
                val alice = startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, packages = cordappsVersionAtStartup).getOrThrow()
                val bob = startNode(rpcUsers = listOf(user), providedName = BOB_NAME, packages = cordappsVersionAtStartup).getOrThrow()
                alice.stop()
                CordaRPCClient(bob.rpcAddress).start(user.username, user.password).use {
                    val flowTracker = it.proxy.startTrackedFlow(::SendMessageFlowY, message, defaultNotaryIdentity, alice.nodeInfo.singleIdentity()).progress
                    //wait until Bob progresses as far as possible because alice node is off
                    flowTracker.takeFirst { it == SendMessageFlowY.Companion.FINALISING_TRANSACTION.label }.toBlocking().single()
                }
                bob.stop()
            }()
            //CordappLoader.invalidateAll()
            val result =  {
                //Bob will resume the flow
                val alice = startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, packages = cordappsVersionAtRestart, customOverrides = mapOf( "devMode" to false)).getOrThrow()
                startNode(providedName = BOB_NAME, rpcUsers = listOf(user), packages = cordappsVersionAtRestart, customOverrides = mapOf("devMode" to false)).getOrThrow()
                CordaRPCClient(alice.rpcAddress).start(user.username, user.password).use {
                    val page = it.proxy.vaultTrack(MessageState::class.java)
                    if (page.snapshot.states.isNotEmpty()) {
                        page.snapshot.states.first()
                    } else {
                        val r = page.updates.timeout(10, TimeUnit.SECONDS).take(1).toBlocking().single()
                        if (r.consumed.isNotEmpty()) r.consumed.first() else r.produced.first()
                    }
                }
            }()
            result
        }
    }

    @Test
    fun `restart nodes with sunspended flow`() {

        val stateAndRef = nodes_interaction(message, listOf("net.test.cordapp.v1"), listOf("net.test.cordapp.v1"))
        assertNotNull(stateAndRef)
        assertEquals(message, stateAndRef!!.state.data.message)
    }

    @Test
    fun `restart nodes with incompatible version of sunspended flow`() {
//        assertFailsWith(RuntimeException::class) {
//            val stateAndRef =  nodes_interaction(message, listOf("net.test.cordapp.v2"), listOf("net.test.cordapp"))
//            //assertNotNull(stateAndRef)
//        //println(stateAndRef!!.state.data)
//        }

        val cordappsVersionAtStartup = listOf("net.test.cordapp.v1")
        val cordappsVersionAtRestart = listOf("net.test.cordapp")

        val user = User("mark", "dadada", setOf(Permissions.startFlow<SendMessageFlowY>(), Permissions.invokeRpc("vaultQuery"), Permissions.invokeRpc("vaultTrack")))
        return driver(DriverParameters(isDebug = true, startNodesInProcess = false,//isQuasarAgentSpecified(),
                portAllocation = RandomFree, extraCordappPackagesToScan = listOf(MessageState::class.packageName))) {
            val logFolder = {
                val alice = startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, packages = cordappsVersionAtStartup).getOrThrow()
                val bob = startNode(rpcUsers = listOf(user), providedName = BOB_NAME, packages = cordappsVersionAtStartup).getOrThrow()
                alice.stop()
                CordaRPCClient(bob.rpcAddress).start(user.username, user.password).use {
                    val flowTracker = it.proxy.startTrackedFlow(::SendMessageFlowY, message, defaultNotaryIdentity, alice.nodeInfo.singleIdentity()).progress
                    //wait until Bob progresses as far as possible because alice node is off
                    flowTracker.takeFirst { it == SendMessageFlowY.Companion.FINALISING_TRANSACTION.label }.toBlocking().single()
                }


                val logFolder = bob.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME


                bob.stop()
                 logFolder
            }()
            //CordappLoader.invalidateAll()

            startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, packages = cordappsVersionAtRestart, customOverrides = mapOf("devMode" to false)).getOrThrow()
            assertFailsWith(net.corda.testing.node.internal.ListenProcessDeathException::class) {
                    startNode(providedName = BOB_NAME, rpcUsers = listOf(user), packages = cordappsVersionAtRestart, customOverrides = mapOf("devMode" to false)).getOrThrow()
            }

            val logFile = logFolder.list { it.filter { it.fileName.toString().endsWith(".log") }.findAny().get() }
            // We count the number of nodes that wrote into the logfile by counting "Logs can be found in"
            val numberOfNodesThatLogged = logFile.readLines { it.filter { "that is incompatible with the current installed version of" in it }.count() }
            assertEquals(1, numberOfNodesThatLogged)

        }
    }
}
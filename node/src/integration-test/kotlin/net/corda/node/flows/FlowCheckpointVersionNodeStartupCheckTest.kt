package net.corda.node.flows

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.readLines
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.CheckpointIncompatibleException
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testMessage.Message
import net.corda.testMessage.MessageState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.TestCorDapp
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.ListenProcessDeathException
import net.test.cordapp.v1.SendMessageFlow
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FlowCheckpointVersionNodeStartupCheckTest {
    companion object {
        val message = Message("Hello world!")
        val classes = setOf(net.corda.testMessage.MessageState::class.java,
                net.corda.testMessage.MessageContract::class.java,
                net.test.cordapp.v1.SendMessageFlow::class.java,
                net.test.cordapp.v1.Record::class.java)
        val user = User("mark", "dadada", setOf(startFlow<SendMessageFlow>(), invokeRpc("vaultQuery"), invokeRpc("vaultTrack")))
    }

    @Test
    fun `restart node successfully with suspended flow`() {

        val cordapps = setOf(TestCorDapp.Factory.create("testJar", "1.0", classes = classes))

        return driver(DriverParameters(isDebug = true, startNodesInProcess = false, inMemoryDB = false, cordappsForAllNodes = cordapps)) {
            {
                val alice = startNode(rpcUsers = listOf(user), providedName = ALICE_NAME).getOrThrow()
                val bob = startNode(rpcUsers = listOf(user), providedName = BOB_NAME).getOrThrow()
                alice.stop()
                CordaRPCClient(bob.rpcAddress).start(user.username, user.password).use {
                    val flowTracker = it.proxy.startTrackedFlow(::SendMessageFlow, message, defaultNotaryIdentity, alice.nodeInfo.singleIdentity()).progress
                    //wait until Bob progresses as far as possible because alice node is off
                    flowTracker.takeFirst { it == SendMessageFlow.Companion.FINALISING_TRANSACTION.label }.toBlocking().single()
                }
                bob.stop()
            }()
            val result = {
                //Bob will resume the flow
                val alice = startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, customOverrides = mapOf("devMode" to false)).getOrThrow()
                startNode(providedName = BOB_NAME, rpcUsers = listOf(user), customOverrides = mapOf("devMode" to false)).getOrThrow()
                CordaRPCClient(alice.rpcAddress).start(user.username, user.password).use {
                    val page = it.proxy.vaultTrack(MessageState::class.java)
                    if (page.snapshot.states.isNotEmpty()) {
                        page.snapshot.states.first()
                    } else {
                        val r = page.updates.timeout(5, TimeUnit.SECONDS).take(1).toBlocking().single()
                        if (r.consumed.isNotEmpty()) r.consumed.first() else r.produced.first()
                    }
                }
            }()
            assertNotNull(result)
            assertEquals(message, result.state.data.message)
        }
    }

    private fun assertNodeRestartFailure(
            cordapps: Set<TestCorDapp>?,
            cordappsVersionAtStartup: Set<TestCorDapp>,
            cordappsVersionAtRestart: Set<TestCorDapp>,
            reuseAdditionalCordappsAtRestart: Boolean,
            assertNodeLogs: String
    ) {

        return driver(DriverParameters(
                startNodesInProcess = false, // start nodes in separate processes to ensure CordappLoader is not shared between restarts
                inMemoryDB = false, // ensure database is persisted between node restarts so we can keep suspended flow in Bob's node
                cordappsForAllNodes = cordapps)
        ) {
            val bobLogFolder = {
                val alice = startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, additionalCordapps = cordappsVersionAtStartup).getOrThrow()
                val bob = startNode(rpcUsers = listOf(user), providedName = BOB_NAME, additionalCordapps = cordappsVersionAtStartup).getOrThrow()
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

            startNode(rpcUsers = listOf(user), providedName = ALICE_NAME, customOverrides = mapOf("devMode" to false),
                    additionalCordapps = cordappsVersionAtRestart, regenerateCordappsOnStart = !reuseAdditionalCordappsAtRestart).getOrThrow()

            assertFailsWith(ListenProcessDeathException::class) {
                startNode(providedName = BOB_NAME, rpcUsers = listOf(user), customOverrides = mapOf("devMode" to false),
                        additionalCordapps = cordappsVersionAtRestart, regenerateCordappsOnStart = !reuseAdditionalCordappsAtRestart).getOrThrow()
            }

            val logFile = bobLogFolder.list { it.filter { it.fileName.toString().endsWith(".log") }.findAny().get() }
            val numberOfNodesThatLogged = logFile.readLines { it.filter { assertNodeLogs in it }.count() }
            assertEquals(1, numberOfNodesThatLogged)
        }
    }

    @Test
    fun `restart nodes with incompatible version of suspended flow due to different jar name`() {

        assertNodeRestartFailure(
                emptySet(),
                setOf(TestCorDapp.Factory.create("testJar", "1.0", classes = classes)),
                setOf(TestCorDapp.Factory.create("testJar2", "1.0", classes = classes)),
                false,
                CheckpointIncompatibleException.FlowNotInstalledException(SendMessageFlow::class.java).message)
    }

    @Test
    fun `restart nodes with incompatible version of suspended flow`() {

        assertNodeRestartFailure(
                emptySet(),
                setOf(TestCorDapp.Factory.create("testJar", "1.0", classes = classes)),
                setOf(TestCorDapp.Factory.create("testJar", "1.0", classes = classes + net.test.cordapp.v1.SendMessageFlow::class.java)),
                false,
                // the part of the log message generated by CheckpointIncompatibleException.FlowVersionIncompatibleException
                "that is incompatible with the current installed version of")
    }

    @Test
    fun `restart nodes with incompatible version of suspended flow due to different timestamps only`() {

        assertNodeRestartFailure(
                emptySet(),
                setOf(TestCorDapp.Factory.create("testJar", "1.0", classes = classes)),
                setOf(TestCorDapp.Factory.create("testJar", "1.0", classes = classes)),
                false,
                // the part of the log message generated by CheckpointIncompatibleException.FlowVersionIncompatibleException
                "that is incompatible with the current installed version of")
    }
}
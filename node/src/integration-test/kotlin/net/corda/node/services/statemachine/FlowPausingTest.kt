package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.concurrent.Semaphore
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FlowPausingTest {

    companion object {
        val TOTAL_MESSAGES = 100
        val SLEEP_BETWEEN_MESSAGES_MS = 10L
    }

    @Test(timeout = 300_000)
    fun `Paused flows can recieve session messages`() {
        val rpcUser = User("demo", "demo", setOf(Permissions.startFlow<HardRestartTest.Ping>(), Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true, inMemoryDB = false)) {
            val alice = startNode(NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser))).getOrThrow()
            val bob = startNode(NodeParameters(providedName = BOB_NAME, rpcUsers = listOf(rpcUser)))
            val startedBob = bob.getOrThrow()
            val aliceFlow = alice.rpc.startFlow(::HeartbeatFlow, startedBob.nodeInfo.legalIdentities[0])
            // We wait here for the initiated flow to start running on bob
            val initiatedFlowId = startedBob.rpc.waitForFlowToStart(150)
            assertNotNull(initiatedFlowId)
            /* We shut down bob, we want this to happen before bob has finished receiving all of the heartbeats.
               This is a Race but if bob finishes too quickly then we will fail to unpause the initiated flow running on BOB latter
               and this test will fail.*/
            startedBob.stop()
            //Start bob backup in Safe mode. This means no flows will run but BOB should receive messages and queue these up.
            val restartedBob = startNode(NodeParameters(
                    providedName = BOB_NAME,
                    rpcUsers = listOf(rpcUser),
                    customOverrides = mapOf("smmStartMode" to "Safe"))).getOrThrow()

            //Sleep for long enough so BOB has time to receive all the messages.
            //All messages in this period should be queued up and replayed when the flow is unpaused.
            Thread.sleep(TOTAL_MESSAGES * SLEEP_BETWEEN_MESSAGES_MS)
            //ALICE should not have finished yet as the HeartbeatResponderFlow should not have sent the final message back (as it is paused).
            assertEquals(false, aliceFlow.returnValue.isDone)
            assertEquals(true, (restartedBob.rpc as InternalCordaRPCOps).unPauseFlow(initiatedFlowId!!))

            assertEquals(true, aliceFlow.returnValue.getOrThrow())
            alice.stop()
            restartedBob.stop()
        }
    }

    fun CordaRPCOps.waitForFlowToStart(maxTrys: Int): StateMachineRunId? {
        for (i in 1..maxTrys) {
            val snapshot = this.stateMachinesSnapshot().singleOrNull()
            if (snapshot == null) {
                Thread.sleep(SLEEP_BETWEEN_MESSAGES_MS)
            } else {
                return snapshot.id
            }
        }
        return null
    }

    @StartableByRPC
    @InitiatingFlow
    class HeartbeatFlow(private val otherParty: Party): FlowLogic<Boolean>() {
        var sequenceNumber = 0
        @Suspendable
        override fun call(): Boolean {
            val session = initiateFlow(otherParty)
            for (i in 1..TOTAL_MESSAGES) {
                session.send(sequenceNumber++)
                sleep(Duration.ofMillis(10))
            }
            val success = session.receive<Boolean>().unwrap{data -> data}
            return success
        }
    }

    @InitiatedBy(HeartbeatFlow::class)
    class HeartbeatResponderFlow(val session: FlowSession): FlowLogic<Unit>() {
        var sequenceNumber : Int = 0
        @Suspendable
        override fun call() {
            var pass = true
            for (i in 1..TOTAL_MESSAGES) {
                val receivedSequenceNumber = session.receive<Int>().unwrap{data -> data}
                if (receivedSequenceNumber != sequenceNumber) {
                    pass = false
                }
                sequenceNumber++
            }
            session.send(pass)
        }
    }
}
package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

class FlowPausingTest {

    //TODO: Eventually I can remove this test/replace it with
    @Test(timeout = 300_000)
    fun `Flow does not run when the node is restarted`() {
        val rpcUser = User("demo", "demo", setOf(Permissions.startFlow<HardRestartTest.Ping>(), Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {
            val alice = startNode(NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser))).getOrThrow()
            val flowId = alice.rpc.startFlow(::SleepingFlow).id
            alice.stop()
            val restartedAlice = startNode(NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser), customOverrides = mapOf("smmStartMode" to "Safe"))).getOrThrow()
            val snapshot = restartedAlice.rpc.stateMachinesSnapshot()
            assertEquals(0, snapshot.size)
        }
    }

    @StartableByRPC
    class SleepingFlow(): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            sleep(Duration.ofSeconds(60))
        }
    }

}
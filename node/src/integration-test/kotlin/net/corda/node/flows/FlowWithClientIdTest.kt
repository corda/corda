package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FlowWithClientIdTest {

    @Before
    fun reset() {
        ResultFlow.hook = null
    }

    @Test(timeout=300_000)
    fun `start flow with client id`() {
        val clientID = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()
            val flowHandle = nodeA.rpc.startFlowDynamicWithClientId(clientID, ResultFlow::class.java, 5)

            assertEquals(5, flowHandle.returnValue.getOrThrow(20.seconds))
            assertEquals(clientID, flowHandle.clientID)
        }
    }

    @Test(timeout=300_000)
    fun `remove client id`() {
        val clientID = UUID.randomUUID().toString()
        var counter = 0
        ResultFlow.hook = { counter++ }
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()

            val flowHandle0 = nodeA.rpc.startFlowDynamicWithClientId(clientID, ResultFlow::class.java, 5)
            flowHandle0.returnValue.getOrThrow(20.seconds)

            val removed = nodeA.rpc.removeClientId(clientID)

            val flowHandle1 = nodeA.rpc.startFlowDynamicWithClientId(clientID, ResultFlow::class.java, 5)
            flowHandle1.returnValue.getOrThrow(20.seconds)

            assertTrue(removed)
            assertNotEquals(flowHandle0.id, flowHandle1.id)
            assertEquals(flowHandle0.clientID, flowHandle1.clientID)
            assertEquals(2, counter) // this asserts that 2 different flows were spawned indeed
        }

    }

}

@StartableByRPC
internal class ResultFlow<A>(private val result: A): FlowLogic<A>() {
    companion object {
        var hook: (() -> Unit)? = null
        var suspendableHook: FlowLogic<Unit>? = null
    }

    @Suspendable
    override fun call(): A {
        hook?.invoke()
        suspendableHook?.let { subFlow(it) }
        return result
    }
}


package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.startFlowWithClientId
import net.corda.core.serialization.ResultSerializationException
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FlowWithClientIdTest {

    @Before
    fun reset() {
        ResultFlow.hook = null
    }

    @Test(timeout=300_000)
    fun `start flow with client id`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)

            assertEquals(5, flowHandle.returnValue.getOrThrow(20.seconds))
            assertEquals(clientId, flowHandle.clientId)
        }
    }

    @Test(timeout=300_000)
    fun `remove client id`() {
        val clientId = UUID.randomUUID().toString()
        var counter = 0
        ResultFlow.hook = { counter++ }
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()

            val flowHandle0 = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)
            flowHandle0.returnValue.getOrThrow(20.seconds)

            val removed = nodeA.rpc.removeClientId(clientId)

            val flowHandle1 = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)
            flowHandle1.returnValue.getOrThrow(20.seconds)

            assertTrue(removed)
            assertNotEquals(flowHandle0.id, flowHandle1.id)
            assertEquals(flowHandle0.clientId, flowHandle1.clientId)
            assertEquals(2, counter) // this asserts that 2 different flows were spawned indeed
        }
    }

    @Test(timeout=300_000)
    fun `on flow unserializable result a 'CordaRuntimeException' is thrown containing in its message the unserializable type`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()

            val e = assertFailsWith<ResultSerializationException> {
                nodeA.rpc.startFlowWithClientId(clientId, ::UnserializableResultFlow).returnValue.getOrThrow(20.seconds)
            }

            val errorMessage = e.message
            assertTrue(errorMessage!!.contains("Unable to create an object serializer for type class ${UnserializableResultFlow.UNSERIALIZABLE_OBJECT::class.java.name}"))
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

@StartableByRPC
internal class UnserializableResultFlow: FlowLogic<OpenFuture<Observable<Unit>>>() {
    companion object {
        val UNSERIALIZABLE_OBJECT = openFuture<Observable<Unit>>().also { it.set(Observable.empty<Unit>())}
    }

    @Suspendable
    override fun call(): OpenFuture<Observable<Unit>> {
        return UNSERIALIZABLE_OBJECT
    }
}

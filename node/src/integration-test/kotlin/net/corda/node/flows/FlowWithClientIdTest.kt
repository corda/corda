package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.ResultSerializationException
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.startFlowWithClientId
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions
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

    @Test(timeout=300_000)
    fun `If flow has an unserializable exception result then it gets converted into a 'CordaRuntimeException'`() {
        ResultFlow.hook = {
            throw UnserializableException()
        }
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val node = startNode().getOrThrow()

            // the below exception is the one populating the flows future. It will get serialized on node jvm, sent over to client and
            // deserialized on client's.
            val e0 = assertFailsWith<CordaRuntimeException> {
                node.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5).returnValue.getOrThrow()
            }

            // the below exception is getting fetched from the database first, and deserialized on node's jvm,
            // then serialized on node jvm, sent over to client and deserialized on client's.
            val e1 = assertFailsWith<CordaRuntimeException> {
                node.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5).returnValue.getOrThrow()
            }

            assertTrue(e0 !is UnserializableException)
            assertTrue(e1 !is UnserializableException)
            assertEquals(UnserializableException::class.java.name, e0.originalExceptionClassName)
            assertEquals(UnserializableException::class.java.name, e1.originalExceptionClassName)
        }
    }

    @Test(timeout=300_000)
    fun `reattachFlowWithClientId can retrieve results from existing flow future`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)
            val reattachedFlowHandle = nodeA.rpc.reattachFlowWithClientId<Int>(clientId)
            assertEquals(5, flowHandle.returnValue.getOrThrow(20.seconds))
            assertEquals(clientId, flowHandle.clientId)
            assertEquals(flowHandle.id, reattachedFlowHandle?.id)
            assertEquals(flowHandle.returnValue.get(), reattachedFlowHandle?.returnValue?.get())
        }
    }

    @Test(timeout = 300_000)
    fun `reattachFlowWithClientId can retrieve exception from existing flow future`() {
        ResultFlow.hook = { throw IllegalStateException("Bla bla bla") }
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)
            val reattachedFlowHandle = nodeA.rpc.reattachFlowWithClientId<Int>(clientId)

            // [CordaRunTimeException] returned because [IllegalStateException] is not serializable
            Assertions.assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                flowHandle.returnValue.getOrThrow(20.seconds)
            }.withMessage("java.lang.IllegalStateException: Bla bla bla")

            Assertions.assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                reattachedFlowHandle?.returnValue?.getOrThrow()
            }.withMessage("java.lang.IllegalStateException: Bla bla bla")
        }
    }

    @Test(timeout=300_000)
    fun `finishedFlowsWithClientIds returns completed flows with client ids`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()
            nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)
            val finishedFlows = nodeA.rpc.finishedFlowsWithClientIds()
            assertEquals(true, finishedFlows[clientId])
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

internal class UnserializableException(
    val unserializableObject: BrokenMap<Unit, Unit> = BrokenMap()
): CordaRuntimeException("123")
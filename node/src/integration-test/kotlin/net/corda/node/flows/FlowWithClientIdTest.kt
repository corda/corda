package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.KilledFlowException
import net.corda.core.flows.ResultSerializationException
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.FlowHandleWithClientId
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startFlowWithClientId
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.statemachine.Checkpoint
import net.corda.nodeapi.exceptions.RejectedCommandException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass
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

    @Test(timeout=300_000)
    fun `a client id flow can be re-attached when flows draining mode is on`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()
            val result0 = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)
            assertEquals(5, result0)

            nodeA.rpc.setFlowsDrainingModeEnabled(true)
            val result1 = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)
            assertEquals(5, result1)
        }
    }

    @Test(timeout=300_000)
    fun `if client id flow does not exist and flows draining mode is on, a RejectedCommandException gets thrown`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()

            nodeA.rpc.setFlowsDrainingModeEnabled(true)
            assertFailsWith<RejectedCommandException>("Node is draining before shutdown. Cannot start new flows through RPC.") {
                nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `a killed flow's exception can be retrieved after restarting the node`() {
        val clientId = UUID.randomUUID().toString()

        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet(), inMemoryDB = false)) {
            val nodeA = startNode(providedName = ALICE_NAME).getOrThrow()
            var flowHandle0: FlowHandleWithClientId<Unit>? = null
            assertFailsWith<KilledFlowException> {
                flowHandle0 = nodeA.rpc.startFlowWithClientId(clientId, ::HospitalizeFlow)
                nodeA.waitForOvernightObservation(flowHandle0!!.id, 20.seconds)
                nodeA.rpc.killFlow(flowHandle0!!.id)
                flowHandle0!!.returnValue.getOrThrow(20.seconds)
            }

            val flowHandle1: FlowHandleWithClientId<Unit> = nodeA.rpc.startFlowWithClientId(clientId, ::HospitalizeFlow)
            assertFailsWith<KilledFlowException> {
                flowHandle1.returnValue.getOrThrow(20.seconds)
            }

            assertEquals(flowHandle0!!.id, flowHandle1.id)
            assertTrue(nodeA.hasStatus(flowHandle0!!.id, Checkpoint.FlowStatus.KILLED))
            assertTrue(nodeA.hasException(flowHandle0!!.id, KilledFlowException::class))

            nodeA.stop()
            val nodeARestarted = startNode(providedName = ALICE_NAME).getOrThrow()

            assertFailsWith<KilledFlowException> {
                nodeARestarted.rpc.reattachFlowWithClientId<Unit>(clientId)!!.returnValue.getOrThrow(20.seconds)
            }
        }
    }

    private fun NodeHandle.hasStatus(id: StateMachineRunId, status: Checkpoint.FlowStatus): Boolean {
        return rpc.startFlow(::IsFlowInStatus, id, status.ordinal).returnValue.getOrThrow(20.seconds)
    }

    private fun <T: Exception> NodeHandle.hasException(id: StateMachineRunId, type: KClass<T>): Boolean {
        return rpc.startFlow(::GetExceptionType, id).returnValue.getOrThrow(20.seconds) == type.qualifiedName
    }

    private fun NodeHandle.waitForOvernightObservation(id: StateMachineRunId, timeout: Duration) {
        val timeoutTime = Instant.now().plusSeconds(timeout.seconds)
        var exists = false
        while (Instant.now().isBefore(timeoutTime) && !exists) {
            exists = rpc.startFlow(::IsFlowInStatus, id, Checkpoint.FlowStatus.HOSPITALIZED.ordinal).returnValue.getOrThrow(timeout)
            Thread.sleep(1.seconds.toMillis())
        }
        if (!exists) {
            throw TimeoutException("Flow was not kept for observation during timeout duration")
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

    @StartableByRPC
    internal class HospitalizeFlow: FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            throw HospitalizeFlowException("time to go to the doctors")
        }
    }

    @StartableByRPC
    internal class IsFlowInStatus(private val id: StateMachineRunId, private val ordinal: Int): FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            return serviceHub.jdbcSession().prepareStatement("select count(*) from node_checkpoints where status = ? and flow_id = ?")
                .apply {
                    setInt(1, ordinal)
                    setString(2, id.uuid.toString())
                }
                .use { ps ->
                    ps.executeQuery().use { rs ->
                        rs.next()
                        rs.getLong(1)
                    }
                }.toInt() == 1
        }
    }

    @StartableByRPC
    internal class GetExceptionType(private val id: StateMachineRunId): FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            return serviceHub.jdbcSession().prepareStatement("select type from node_flow_exceptions where flow_id = ?")
                .apply { setString(1, id.uuid.toString()) }
                .use { ps ->
                    ps.executeQuery().use { rs ->
                        rs.next()
                        rs.getString(1)
                    }

                }
        }
    }

    internal class UnserializableException(
        val unserializableObject: BrokenMap<Unit, Unit> = BrokenMap()
    ): CordaRuntimeException("123")
}
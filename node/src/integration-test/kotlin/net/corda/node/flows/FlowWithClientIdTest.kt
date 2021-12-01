package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.PermissionException
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.KilledFlowException
import net.corda.core.flows.ResultSerializationException
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.doOnError
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.FlowHandleWithClientId
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startFlowWithClientId
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions
import net.corda.node.services.statemachine.Checkpoint
import net.corda.nodeapi.exceptions.RejectedCommandException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowWithClientIdTest {

    @Before
    fun reset() {
        ResultFlow.hook = null
    }

    @Test(timeout = 300_000)
    fun `start flow with client id`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode().getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)

            assertEquals(5, flowHandle.returnValue.getOrThrow(20.seconds))
            assertEquals(clientId, flowHandle.clientId)
        }
    }

    @Test(timeout = 300_000)
    fun `start flow with client id permissions - StartFlow`() {
        val user = User("TonyStark", "I AM IRONMAN", setOf("StartFlow.net.corda.node.flows.FlowWithClientIdTest\$ResultFlow"))
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user)).getOrThrow()
            nodeA.rpc.startFlowWithClientId(UUID.randomUUID().toString(), ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)
            nodeA.rpc.startFlowDynamicWithClientId(
                UUID.randomUUID().toString(),
                ResultFlow::class.java,
                5
            ).returnValue.getOrThrow(20.seconds)
        }
    }

    @Test(timeout = 300_000)
    fun `start flow with client id permissions - InvokeRpc-startFlowWithClientId`() {
        val user = User("TonyStark", "I AM IRONMAN", setOf("InvokeRpc.startFlowWithClientId"))
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user)).getOrThrow()
            nodeA.rpc.startFlowWithClientId(UUID.randomUUID().toString(), ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)
            nodeA.rpc.startFlowDynamicWithClientId(
                UUID.randomUUID().toString(),
                ResultFlow::class.java,
                5
            ).returnValue.getOrThrow(20.seconds)
        }
    }

    @Test(timeout = 300_000)
    fun `start flow with client id permissions - InvokeRpc-startFlowDynamicWithClientId`() {
        val user = User("TonyStark", "I AM IRONMAN", setOf("InvokeRpc.startFlowDynamicWithClientId"))
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user)).getOrThrow()
            nodeA.rpc.startFlowWithClientId(UUID.randomUUID().toString(), ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)
            nodeA.rpc.startFlowDynamicWithClientId(
                UUID.randomUUID().toString(),
                ResultFlow::class.java,
                5
            ).returnValue.getOrThrow(20.seconds)
        }
    }

    @Test(timeout = 300_000)
    fun `start flow with client id without permissions`() {
        val user = User("TonyStark", "I AM IRONMAN", setOf("InvokeRpc.startFlow"))
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user)).getOrThrow()
            assertFailsWith<PermissionException> {
                nodeA.rpc.startFlowWithClientId(UUID.randomUUID().toString(), ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)
            }
            assertFailsWith<PermissionException> {
                nodeA.rpc.startFlowDynamicWithClientId(
                    UUID.randomUUID().toString(),
                    ResultFlow::class.java,
                    5
                ).returnValue.getOrThrow(20.seconds)
            }
        }
    }

    @Test(timeout = 300_000)
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

    @Test(timeout = 300_000)
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

    @Test(timeout = 300_000)
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

    @Test(timeout = 300_000)
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

    @Test(timeout = 300_000)
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

    private fun <T : Exception> NodeHandle.hasException(id: StateMachineRunId, type: KClass<T>): Boolean {
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

    @Test(timeout = 300_000)
    fun `reattaching to existing running flow using startFlowWithClientId for flow started by another user throws a permission exception`() {
        val user = User("TonyStark", "I AM IRONMAN", setOf(Permissions.all()))
        val spy = User("spy", "l33t h4ck4r", setOf(Permissions.all()))
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user, spy)).getOrThrow()
            val latch = CountDownLatch(1)
            ResultFlow.hook = {
                latch.await()
            }
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)
            val reattachedByStarter = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)

            assertFailsWith<PermissionException> {
                CordaRPCClient(nodeA.rpcAddress).start(spy.username, spy.password).use {
                    it.proxy.startFlowWithClientId(clientId, ::ResultFlow, 5)
                }
            }

            latch.countDown()

            assertEquals(5, flowHandle.returnValue.getOrThrow(20.seconds))
            assertEquals(5, reattachedByStarter.returnValue.getOrThrow(20.seconds))
        }
    }

    @Test(timeout = 300_000)
    fun `reattaching to existing completed flow using startFlowWithClientId for flow started by another user throws a permission exception`() {
        val user = User("TonyStark", "I AM IRONMAN", setOf(Permissions.all()))
        val spy = User("spy", "l33t h4ck4r", setOf(Permissions.all()))
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user, spy)).getOrThrow()
            nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)

            assertFailsWith<PermissionException> {
                CordaRPCClient(nodeA.rpcAddress).start(spy.username, spy.password).use {
                    it.proxy.startFlowWithClientId(clientId, ::ResultFlow, 5)
                }
            }
        }
    }

    @Test(timeout = 300_000)
    fun `reattaching to existing completed flow using startFlowWithClientId for flow started by another user throws a permission exception (after node restart)`() {
        val user = User("TonyStark", "I AM IRONMAN", setOf(Permissions.all()))
        val spy = User("spy", "l33t h4ck4r", setOf(Permissions.all()))
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet(), inMemoryDB = false)) {
            var nodeA = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user, spy)).getOrThrow()
            nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5).returnValue.getOrThrow(20.seconds)

            nodeA.stop()
            nodeA = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user, spy)).getOrThrow(20.seconds)

            assertFailsWith<PermissionException> {
                CordaRPCClient(nodeA.rpcAddress).start(spy.username, spy.password).use {
                    it.proxy.startFlowWithClientId(clientId, ::ResultFlow, 5)
                }
            }
        }
    }

    @Test(timeout = 300_000)
    fun `reattaching to existing flow using reattachFlowWithClientId for flow started by another user returns null`() {
        val user = User("dan", "this is my password", setOf(Permissions.all()))
        val spy = User("spy", "l33t h4ck4r", setOf(Permissions.all()))
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user, spy)).getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)
            val reattachedByStarter = nodeA.rpc.reattachFlowWithClientId<Int>(clientId)?.returnValue?.getOrThrow(20.seconds)

            val reattachedBySpy = CordaRPCClient(nodeA.rpcAddress).start(spy.username, spy.password).use {
                it.proxy.reattachFlowWithClientId<Int>(clientId)?.returnValue?.getOrThrow(20.seconds)
            }

            assertEquals(5, flowHandle.returnValue.getOrThrow(20.seconds))
            assertEquals(5, reattachedByStarter)
            assertNull(reattachedBySpy)
        }
    }

    @Test(timeout = 300_000)
    fun `removeClientId does not remove mapping for flows started by another user`() {
        val user = User("dan", "this is my password", setOf(Permissions.all()))
        val spy = User("spy", "l33t h4ck4r", setOf(Permissions.all()))
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user, spy)).getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)

            flowHandle.returnValue.getOrThrow(20.seconds)

            val removedBySpy = CordaRPCClient(nodeA.rpcAddress).start(spy.username, spy.password).use {
                it.proxy.removeClientId(clientId)
            }

            val reattachedByStarter = nodeA.rpc.reattachFlowWithClientId<Int>(clientId)?.returnValue?.getOrThrow(20.seconds)
            val removedByStarter = nodeA.rpc.removeClientId(clientId)

            assertEquals(5, reattachedByStarter)
            assertTrue(removedByStarter)
            assertFalse(removedBySpy)
        }
    }

    @Test(timeout = 300_000)
    fun `removeClientIdAsAdmin does remove mapping for flows started by another user`() {
        val user = User("dan", "this is my password", setOf(Permissions.all()))
        val spy = User("spy", "l33t h4ck4r", setOf(Permissions.all()))
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user, spy)).getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::ResultFlow, 5)

            flowHandle.returnValue.getOrThrow(20.seconds)

            val removedBySpy = CordaRPCClient(nodeA.rpcAddress).start(spy.username, spy.password).use {
                it.proxy.removeClientIdAsAdmin(clientId)
            }

            val reattachedByStarter = nodeA.rpc.reattachFlowWithClientId<Int>(clientId)?.returnValue?.getOrThrow(20.seconds)
            val removedByStarter = nodeA.rpc.removeClientIdAsAdmin(clientId)

            assertNull(reattachedByStarter)
            assertFalse(removedByStarter)
            assertTrue(removedBySpy)
        }
    }

    @Test(timeout = 300_000)
    fun `finishedFlowsWithClientIds does not return flows started by other users`() {
        val user = User("CaptainAmerica", "That really is America's ass", setOf(Permissions.all()))
        val spy = User("nsa", "EternalBlue", setOf(Permissions.all()))
        val clientIdForUser = UUID.randomUUID().toString()
        val clientIdForSpy = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user, spy)).getOrThrow()
            val flowHandleStartedByUser = nodeA.rpc.startFlowWithClientId(clientIdForUser, ::ResultFlow, 5)

            CordaRPCClient(nodeA.rpcAddress).start(spy.username, spy.password).use {
                val flowHandleStartedBySpy = it.proxy.startFlowWithClientId(clientIdForSpy, ::ResultFlow, 10)

                flowHandleStartedByUser.returnValue.getOrThrow(20.seconds)
                flowHandleStartedBySpy.returnValue.getOrThrow(20.seconds)

                val userFinishedFlows = nodeA.rpc.finishedFlowsWithClientIds()
                val spyFinishedFlows = it.proxy.finishedFlowsWithClientIds()

                assertEquals(1, userFinishedFlows.size)
                assertEquals(clientIdForUser, userFinishedFlows.keys.single())
                assertEquals(5, nodeA.rpc.reattachFlowWithClientId<Int>(userFinishedFlows.keys.single())!!.returnValue.getOrThrow())
                assertEquals(1, spyFinishedFlows.size)
                assertEquals(clientIdForSpy, spyFinishedFlows.keys.single())
                assertEquals(10, it.proxy.reattachFlowWithClientId<Int>(spyFinishedFlows.keys.single())!!.returnValue.getOrThrow())
            }
        }
    }

    @Test(timeout = 300_000)
    fun `finishedFlowsWithClientIdsAsAdmin does return flows started by other users`() {
        val user = User("CaptainAmerica", "That really is America's ass", setOf(Permissions.all()))
        val spy = User("nsa", "EternalBlue", setOf(Permissions.all()))
        val clientIdForUser = UUID.randomUUID().toString()
        val clientIdForSpy = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptySet())) {
            val nodeA = startNode(rpcUsers = listOf(user, spy)).getOrThrow()
            val flowHandleStartedByUser = nodeA.rpc.startFlowWithClientId(clientIdForUser, ::ResultFlow, 5)

            CordaRPCClient(nodeA.rpcAddress).start(spy.username, spy.password).use {
                val flowHandleStartedBySpy = it.proxy.startFlowWithClientId(clientIdForSpy, ::ResultFlow, 10)

                flowHandleStartedByUser.returnValue.getOrThrow(20.seconds)
                flowHandleStartedBySpy.returnValue.getOrThrow(20.seconds)

                val userFinishedFlows = nodeA.rpc.finishedFlowsWithClientIdsAsAdmin()
                val spyFinishedFlows = it.proxy.finishedFlowsWithClientIdsAsAdmin()

                assertEquals(2, userFinishedFlows.size)
                assertEquals(2, spyFinishedFlows.size)
                assertEquals(userFinishedFlows, spyFinishedFlows)
            }
        }
    }

    // This test is not very realistic because the scenario it happens under is also not very realistic.
    @Test(timeout = 300_000)
    fun `flow started with client id that fails before its first checkpoint that contains an unserializable argument will be persited as FAILED`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val nodeA = startNode(NodeParameters(ALICE_NAME)).getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::QuickFailingFlow, LazyUnserializableObject())
            val reattachedFlowHandle = nodeA.rpc.reattachFlowWithClientId<Int>(clientId)

            assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                flowHandle.returnValue.getOrThrow(20.seconds)
            }.withMessage("I have failed quickly")

            assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                reattachedFlowHandle?.returnValue?.getOrThrow()
            }.withMessage("I have failed quickly")

            assertTrue(nodeA.hasStatus(flowHandle.id, Checkpoint.FlowStatus.FAILED))
            val arguments = nodeA.rpc.startFlow(::GetFlowInitialArgumentsFromMetadata, flowHandle.id).returnValue.getOrThrow(20.seconds)
            assertEquals(arguments.size, 1)
            assertTrue(arguments.single() is LazyUnserializableObject)
        }
    }

    // This test has been added to replicate the exact scenario a user experienced.
    @Test(timeout = 300_000)
    fun `flow started with client id that fails before its first checkpoint with subflow'd flow will be persited as FAILED`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val nodeA = startNode(NodeParameters(ALICE_NAME)).getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::PassedInFailingFlow, SuperQuickFailingFlow())
            val reattachedFlowHandle = nodeA.rpc.reattachFlowWithClientId<Int>(clientId)

            assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                flowHandle.returnValue.getOrThrow(20.seconds)
            }.withMessage("I have failed quickly")

            assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                reattachedFlowHandle?.returnValue?.getOrThrow()
            }.withMessage("I have failed quickly")

            assertTrue(nodeA.hasStatus(flowHandle.id, Checkpoint.FlowStatus.FAILED))
            val arguments = nodeA.rpc.startFlow(::GetFlowInitialArgumentsFromMetadata, flowHandle.id).returnValue.getOrThrow(20.seconds)
            assertEquals(arguments.size, 1)
            assertTrue(arguments.single() is SuperQuickFailingFlow)

        }
    }

    @Test(timeout = 300_000)
    fun `flow started with client id that fails can use doOnError to process the exception`() {
        val clientId = UUID.randomUUID().toString()
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val nodeA = startNode(NodeParameters(ALICE_NAME)).getOrThrow()
            val flowHandle = nodeA.rpc.startFlowWithClientId(clientId, ::SuperQuickFailingFlow)
            val reattachedFlowHandle = nodeA.rpc.reattachFlowWithClientId<Int>(clientId)

            val lock = CountDownLatch(1)
            val reattachedLock = CountDownLatch(1)

            flowHandle.returnValue.doOnError {
                lock.countDown()
            }

            reattachedFlowHandle?.returnValue?.doOnError {
                reattachedLock.countDown()
            }

            assertTrue(lock.await(20, TimeUnit.SECONDS))
            assertTrue(reattachedLock.await(20, TimeUnit.SECONDS))
            assertTrue(flowHandle.returnValue.isDone)
            assertTrue(reattachedFlowHandle!!.returnValue.isDone)
        }
    }

    @CordaSerializable
    @StartableByRPC
    internal class QuickFailingFlow(private val lazyUnserializableObject: LazyUnserializableObject) : FlowLogic<Int>() {

        @Suspendable
        override fun call(): Int {
            lazyUnserializableObject.prop = UnserializableObject()
            throw CordaRuntimeException("I have failed quickly")
        }
    }

    @CordaSerializable
    class LazyUnserializableObject(var prop: UnserializableObject? = null)

    @CordaSerializable
    class UnserializableObject : KryoSerializable {
        override fun write(kryo: Kryo?, output: Output?) {
            throw IllegalStateException("Cannot be serialized")
        }

        override fun read(kryo: Kryo?, input: Input?) {
            throw IllegalStateException("Cannot be read")
        }
    }

    @StartableByRPC
    internal class PassedInFailingFlow(private val flow: SuperQuickFailingFlow) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
           return subFlow(flow)
        }
    }

    @CordaSerializable
    @StartableByRPC
    internal class SuperQuickFailingFlow : FlowLogic<Int>() {

        @Suspendable
        override fun call(): Int {
            throw CordaRuntimeException("I have failed quickly")
        }
    }

    @StartableByRPC
    internal class ResultFlow<A>(private val result: A) : FlowLogic<A>() {
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
    internal class UnserializableResultFlow : FlowLogic<OpenFuture<Observable<Unit>>>() {
        companion object {
            val UNSERIALIZABLE_OBJECT = openFuture<Observable<Unit>>().also { it.set(Observable.empty<Unit>()) }
        }

        @Suspendable
        override fun call(): OpenFuture<Observable<Unit>> {
            return UNSERIALIZABLE_OBJECT
        }
    }

    @StartableByRPC
    internal class HospitalizeFlow : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            throw HospitalizeFlowException("time to go to the doctors")
        }
    }

    @StartableByRPC
    internal class IsFlowInStatus(private val id: StateMachineRunId, private val ordinal: Int) : FlowLogic<Boolean>() {
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
    internal class GetExceptionType(private val id: StateMachineRunId) : FlowLogic<String>() {
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

    @StartableByRPC
    internal class GetFlowInitialArgumentsFromMetadata(private val id: StateMachineRunId) : FlowLogic<List<Any?>>() {
        @Suspendable
        override fun call(): List<Any?> {
            val argumentBytes = serviceHub.jdbcSession().prepareStatement("select flow_parameters from node_flow_metadata where flow_id = ?")
                .apply {
                    setString(1, id.uuid.toString())
                }
                .use { ps ->
                    ps.executeQuery().use { rs ->
                        rs.next()
                        rs.getBytes(1)
                    }
                }
            return argumentBytes.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)
        }
    }

    internal class UnserializableException(
        val unserializableObject: BrokenMap<Unit, Unit> = BrokenMap()
    ) : CordaRuntimeException("123")
}
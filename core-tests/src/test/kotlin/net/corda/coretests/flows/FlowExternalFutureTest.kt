package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.coretests.flows.AbstractFlowExternalResultTest.DirectlyAccessedServiceHubException
import net.corda.coretests.flows.AbstractFlowExternalResultTest.ExternalFuture
import net.corda.coretests.flows.AbstractFlowExternalResultTest.FlowWithExternalProcess
import net.corda.coretests.flows.AbstractFlowExternalResultTest.FutureService
import net.corda.coretests.flows.AbstractFlowExternalResultTest.MyCordaException
import net.corda.node.services.statemachine.StateTransitionException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import java.sql.SQLTransientConnectionException
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowExternalFutureTest : AbstractFlowExternalResultTest() {

    @Test
    fun `external future`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithExternalFuture, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external future that checks deduplicationId is not rerun when flow is retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<DuplicatedProcessException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalFutureWithDeduplication,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external future propagates exception to calling flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalFuturePropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    MyCordaException::class.java
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external future exception can be caught in flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            val result = alice.rpc.startFlow(::FlowWithExternalFutureThatThrowsExceptionAndCaughtInFlow, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(20.seconds)
            assertTrue(result as Boolean)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external future with exception that hospital keeps for observation does not fail`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalFuturePropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    HospitalizeFlowException::class.java
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(1, observation)
        }
    }

    @Test
    fun `external future with exception that hospital discharges is retried and runs the future again`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalFuturePropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    SQLTransientConnectionException::class.java
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(3, discharged)
            assertEquals(1, observation)
        }
    }

    @Test
    fun `external future that throws exception rather than completing future exceptionally fails with internal exception`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<StateTransitionException> {
                alice.rpc.startFlow(::FlowWithExternalFutureUnhandledException, bob.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external future that passes serviceHub into process can be retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalFutureThatPassesInServiceHubCanRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(3, discharged)
            assertEquals(1, observation)
        }
    }

    @Test
    fun `external future that accesses serviceHub from flow directly will fail when retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<DirectlyAccessedServiceHubException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalFutureThatDirectlyAccessesServiceHubFailsRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `starting multiple futures and joining on their results`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowThatStartsMultipleFuturesAndJoins, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }
}

@StartableByRPC
class FlowWithExternalFuture(party: Party) : FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(ExternalFuture(serviceHub) { _, _ ->
            serviceHub.cordaService(FutureService::class.java).createFuture()
        })
}

@StartableByRPC
class FlowWithExternalFuturePropagatesException<T>(party: Party, private val exceptionType: Class<T>) :
    FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(ExternalFuture(serviceHub) { _, _ ->
            openFuture<Any>().apply {
                setException(createException())
            }
        })

    private fun createException() = when (exceptionType) {
        HospitalizeFlowException::class.java -> HospitalizeFlowException("keep it around")
        SQLTransientConnectionException::class.java -> SQLTransientConnectionException("fake exception - connection is not available")
        else -> MyCordaException("boom")
    }
}

@StartableByRPC
class FlowWithExternalFutureThatThrowsExceptionAndCaughtInFlow(party: Party) :
    FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any = try {
        await(ExternalFuture(serviceHub) { _, _ ->
            openFuture<Any>().apply {
                setException(IllegalStateException("threw exception in external future"))
            }
        })
    } catch (e: IllegalStateException) {
        log.info("Exception was caught")
        true
    }
}

@StartableByRPC
class FlowWithExternalFutureUnhandledException(party: Party) : FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(ExternalFuture(serviceHub) { _, _ -> throw MyCordaException("threw exception in external future") })
}

@StartableByRPC
class FlowWithExternalFutureThatPassesInServiceHubCanRetry(party: Party) : FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(ExternalFuture(serviceHub) { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
        })
}

@StartableByRPC
class FlowWithExternalFutureThatDirectlyAccessesServiceHubFailsRetry(party: Party) : FlowWithExternalProcess(party) {

    @Suppress("TooGenericExceptionCaught")
    @Suspendable
    override fun testCode(): Any {
        return await(ExternalFuture(serviceHub) { _, _ ->
            try {
                serviceHub.cordaService(FutureService::class.java).setHospitalHandledException()
            } catch (e: NullPointerException) {
                // Catch the [NullPointerException] thrown from accessing the flow's [ServiceHub]
                // set the future so that the exception can be asserted from the test
                openFuture<Any>().apply { setException(DirectlyAccessedServiceHubException()) }
            }
        })
    }
}

@StartableByRPC
class FlowWithExternalFutureWithDeduplication(party: Party) : FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any {
        return await(ExternalFuture(serviceHub) { serviceHub, deduplicationId ->
            serviceHub.cordaService(FutureService::class.java).createExceptionFutureWithDeduplication(deduplicationId)
        })
    }
}

@StartableByRPC
class FlowThatStartsMultipleFuturesAndJoins(party: Party) : FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(ExternalFuture(serviceHub) { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).startMultipleFuturesAndJoin()
        }.also { log.info("Result - $it") })
}
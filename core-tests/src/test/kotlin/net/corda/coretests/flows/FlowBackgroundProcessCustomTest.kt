package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowBackgroundProcess
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.startFlow
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
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

class FlowBackgroundProcessCustomTest {

    @Test
    fun `custom background process works`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithCustomBackgroundProcess, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `custom background process propagates exception to calling flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(
                    ::FlowWithCustomBackgroundProcessPropagatesException,
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
    fun `custom background process exception can be caught in flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithCustomBackgroundProcessThatThrowsExceptionAndCaughtInFlow, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `custom background process with exception that hospital keeps for observation does not fail`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithCustomBackgroundProcessPropagatesException,
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
    fun `custom background process with exception that hospital discharges is retried and runs the background process again`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithCustomBackgroundProcessPropagatesException,
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
    fun `custom background process that throws exception rather than completing future exceptionally fails with internal exception`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<StateTransitionException> {
                alice.rpc.startFlow(::FlowWithCustomBackgroundProcessUnhandledException, bob.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `custom background process that passes serviceHub into process can be retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithCustomBackgroundProcessThatPassesInServiceHubCanRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(3, discharged)
            assertEquals(1, observation)
        }
    }

    @Test
    fun `custom background process that accesses serviceHub from flow directly will fail when retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<StateTransitionException> {
                alice.rpc.startFlow(
                    ::FlowWithCustomBackgroundProcessThatDirectlyAccessesServiceHubFailsRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }
}

class BackgroundProcess(
    private val serviceHub: ServiceHub,
    private val function: (serviceHub: ServiceHub) -> CordaFuture<Any>
) : FlowBackgroundProcess<Any> {
    override fun execute(deduplicationId: String): CordaFuture<Any> {
        return function(serviceHub)
    }
}

class BadBackgroundProcess(private val function: () -> CordaFuture<Any>) : FlowBackgroundProcess<Any> {
    override fun execute(deduplicationId: String): CordaFuture<Any> {
        return function()
    }
}

@StartableByRPC
class FlowWithCustomBackgroundProcess(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any = await(BackgroundProcess(serviceHub) { it.cordaService(FutureService::class.java).createFuture() })
}

@StartableByRPC
class FlowWithCustomBackgroundProcessPropagatesException<T>(party: Party, private val exceptionType: Class<T>) :
    FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(BackgroundProcess(serviceHub) {
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
class FlowWithCustomBackgroundProcessThatThrowsExceptionAndCaughtInFlow(party: Party) :
    FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any = try {
        await(BackgroundProcess(serviceHub) {
            openFuture<Any>().apply {
                setException(MyCordaException("threw exception in background process"))
            }
        })
    } catch (e: MyCordaException) {
        log.info("Exception was caught")
        "Exception was caught"
    }
}

@StartableByRPC
class FlowWithCustomBackgroundProcessUnhandledException(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any = await(BackgroundProcess(serviceHub) { throw MyCordaException("threw exception in background process") })
}

@StartableByRPC
class FlowWithCustomBackgroundProcessThatPassesInServiceHubCanRetry(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(BackgroundProcess(serviceHub) { it.cordaService(FutureService::class.java).throwHospitalHandledException() })
}

@StartableByRPC
class FlowWithCustomBackgroundProcessThatDirectlyAccessesServiceHubFailsRetry(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(BadBackgroundProcess { serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException() })
}
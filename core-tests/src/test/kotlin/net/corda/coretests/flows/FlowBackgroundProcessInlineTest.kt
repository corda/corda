package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaException
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.await
import net.corda.core.flows.awaitFuture
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
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

class FlowBackgroundProcessInlineTest {

    @Test
    fun `inline function`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithBackgroundProcess, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline future`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(
                ::FlowWithBackgroundProcessStartedByInlineFuture,
                bob.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline starting multiple futures and joining on their results`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::ForkJoinProcesses, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline future that checks deduplicationId is not rerun when flow is retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<DuplicatedProcessException> {
                alice.rpc.startFlow(
                    ::FlowWithBackgroundProcessStartedByInlineFutureThatChecksDeduplicationId,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline function that throws exception is propagated up to the calling flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(
                    ::FlowWithBackgroundProcessThatThrowsException,
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
    fun `inline function that throws exception can be caught and dealt with`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(
                ::FlowWithBackgroundProcessThatThrowsExceptionAndCaughtInFlow,
                bob.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline function with exception that hospital keeps for observation does not fail`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithBackgroundProcessThatThrowsException,
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
    fun `inline function with exception that hospital discharges is retried and runs the background process again`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithBackgroundProcessThatThrowsException,
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
    fun `inline function that uses serviceHub from function can be retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithBackgroundProcessThatUsesServiceHubFromFunctionCanRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(3, discharged)
            assertEquals(1, observation)
        }
    }

    @Test
    fun `inline future function that uses serviceHub from function can be retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithBackgroundProcessThatUsesServiceHubFromFutureFunctionCanRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(3, discharged)
            assertEquals(1, observation)
        }
    }

    @Test
    fun `inline function that accesses serviceHub from flow directly will fail when retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<DirectlyAccessedServiceHubException> {
                alice.rpc.startFlow(
                    ::FlowWithBackgroundProcessThatDirectlyAccessesServiceHubFailsRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }
}

class MyCordaException(message: String) : CordaException(message)

class DuplicatedProcessException(private val deduplicationId: String) : CordaException("Duplicated process: $deduplicationId")

@StartableByRPC
class FlowWithBackgroundProcessStartedByInlineFuture(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any {
        return awaitFuture { serviceHub, deduplicationId ->
            serviceHub.cordaService(FutureService::class.java).createFuture()
        }
    }
}

@StartableByRPC
class FlowWithBackgroundProcessStartedByInlineFutureThatChecksDeduplicationId(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any {
        return awaitFuture { serviceHub, deduplicationId ->
            serviceHub.cordaService(FutureService::class.java).createFutureWithDeduplication(deduplicationId)
        }
    }
}

@StartableByRPC
class ForkJoinProcesses(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        awaitFuture { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).forkJoinProcesses()
        }.also { log.info("Result - $it") }
}

@StartableByRPC
class FlowWithBackgroundProcessThatThrowsException<T : Exception>(party: Party, private val exceptionType: Class<T>) :
    FlowWithBackgroundProcess(party) {

    override fun testCode(): Any = await { _, deduplicationId ->
        logger.info("Inside of background process")
        throw when (exceptionType) {
            HospitalizeFlowException::class.java -> HospitalizeFlowException("keep it around")
            SQLTransientConnectionException::class.java -> SQLTransientConnectionException("fake exception - connection is not available")
            else -> MyCordaException("boom")
        }
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatThrowsExceptionAndCaughtInFlow(party: Party) :
    FlowWithBackgroundProcess(party) {

    override fun testCode(): Any = try {
        await { _, deduplicationId ->
            logger.info("Inside of background process")
            throw MyCordaException("boom")
        }
    } catch (e: MyCordaException) {
        log.info("Exception was caught")
        "Exception was caught"
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatUsesServiceHubFromFunctionCanRetry(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any {
        await { serviceHub, deduplicationId ->
            serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
        }
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatUsesServiceHubFromFutureFunctionCanRetry(party: Party) : FlowWithBackgroundProcess(party) {

    @Suspendable
    override fun testCode(): Any {
        return awaitFuture { serviceHub, deduplicationId ->
            serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
        }
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatDirectlyAccessesServiceHubFailsRetry(party: Party) : FlowWithBackgroundProcess(party) {

    @Suppress("TooGenericExceptionCaught")
    @Suspendable
    override fun testCode(): Any {
        try {
            // use the flow's serviceHub
            await { _, deduplicationId ->
                serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
            }
        } catch (e: NullPointerException) {
            throw DirectlyAccessedServiceHubException()
        }
    }
}

class DirectlyAccessedServiceHubException : CordaException("Null pointer from accessing flow's serviceHub")
package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.coretests.flows.AbstractFlowExternalOperationTest.DirectlyAccessedServiceHubException
import net.corda.coretests.flows.AbstractFlowExternalOperationTest.ExternalAsyncOperation
import net.corda.coretests.flows.AbstractFlowExternalOperationTest.FlowWithExternalProcess
import net.corda.coretests.flows.AbstractFlowExternalOperationTest.FutureService
import net.corda.coretests.flows.AbstractFlowExternalOperationTest.MyCordaException
import net.corda.node.services.statemachine.StateTransitionException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import java.sql.SQLTransientConnectionException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowExternalAsyncOperationTest : AbstractFlowExternalOperationTest() {

    @Test
    fun `external async operation`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithExternalAsyncOperation, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external async operation that checks deduplicationId is not rerun when flow is retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<DuplicatedProcessException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationWithDeduplication,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external async operation propagates exception to calling flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationPropagatesException,
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
    fun `external async operation exception can be caught in flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            val result = alice.rpc.startFlow(
                ::FlowWithExternalAsyncOperationThatThrowsExceptionAndCaughtInFlow,
                bob.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(20.seconds)
            assertTrue(result as Boolean)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external async operation with exception that hospital keeps for observation does not fail`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationPropagatesException,
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
    fun `external async operation with exception that hospital discharges is retried and runs the future again`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationPropagatesException,
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
    fun `external async operation that throws exception rather than completing future exceptionally fails with internal exception`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<StateTransitionException> {
                alice.rpc.startFlow(::FlowWithExternalAsyncOperationUnhandledException, bob.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external async operation that passes serviceHub into process can be retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationThatPassesInServiceHubCanRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(3, discharged)
            assertEquals(1, observation)
        }
    }

    @Test
    fun `external async operation that accesses serviceHub from flow directly will fail when retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<DirectlyAccessedServiceHubException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationThatDirectlyAccessesServiceHubFailsRetry,
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

    @StartableByRPC
    class FlowWithExternalAsyncOperation(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any =
            await(ExternalAsyncOperation(serviceHub) { _, _ ->
                serviceHub.cordaService(FutureService::class.java).createFuture()
            })
    }

    @StartableByRPC
    class FlowWithExternalAsyncOperationPropagatesException<T>(party: Party, private val exceptionType: Class<T>) :
        FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any =
            await(ExternalAsyncOperation(serviceHub) { _, _ ->
                CompletableFuture<Any>().apply {
                    completeExceptionally(createException())
                }
            })

        private fun createException() = when (exceptionType) {
            HospitalizeFlowException::class.java -> HospitalizeFlowException("keep it around")
            SQLTransientConnectionException::class.java -> SQLTransientConnectionException("fake exception - connection is not available")
            else -> MyCordaException("boom")
        }
    }

    @StartableByRPC
    class FlowWithExternalAsyncOperationThatThrowsExceptionAndCaughtInFlow(party: Party) :
        FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any = try {
            await(ExternalAsyncOperation(serviceHub) { _, _ ->
                CompletableFuture<Any>().apply {
                    completeExceptionally(IllegalStateException("threw exception in external async operation"))
                }
            })
        } catch (e: IllegalStateException) {
            log.info("Exception was caught")
            true
        }
    }

    @StartableByRPC
    class FlowWithExternalAsyncOperationUnhandledException(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any =
            await(ExternalAsyncOperation(serviceHub) { _, _ -> throw MyCordaException("threw exception in external async operation") })
    }

    @StartableByRPC
    class FlowWithExternalAsyncOperationThatPassesInServiceHubCanRetry(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any =
            await(ExternalAsyncOperation(serviceHub) { serviceHub, _ ->
                serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
            })
    }

    @StartableByRPC
    class FlowWithExternalAsyncOperationThatDirectlyAccessesServiceHubFailsRetry(party: Party) : FlowWithExternalProcess(party) {

        @Suppress("TooGenericExceptionCaught")
        @Suspendable
        override fun testCode(): Any {
            return await(ExternalAsyncOperation(serviceHub) { _, _ ->
                try {
                    serviceHub.cordaService(FutureService::class.java).setHospitalHandledException()
                } catch (e: NullPointerException) {
                    // Catch the [NullPointerException] thrown from accessing the flow's [ServiceHub]
                    // set the future so that the exception can be asserted from the test
                    CompletableFuture<Any>().apply { completeExceptionally(DirectlyAccessedServiceHubException()) }
                }
            })
        }
    }

    @StartableByRPC
    class FlowWithExternalAsyncOperationWithDeduplication(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any {
            return await(ExternalAsyncOperation(serviceHub) { serviceHub, deduplicationId ->
                serviceHub.cordaService(FutureService::class.java).createExceptionFutureWithDeduplication(deduplicationId)
            })
        }
    }

    @StartableByRPC
    class FlowThatStartsMultipleFuturesAndJoins(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any =
            await(ExternalAsyncOperation(serviceHub) { serviceHub, _ ->
                serviceHub.cordaService(FutureService::class.java).startMultipleFuturesAndJoin()
            }.also { log.info("Result - $it") })
    }
}
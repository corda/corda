package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.node.services.statemachine.StateTransitionException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import java.sql.SQLTransientConnectionException
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowExternalAsyncOperationTest : AbstractFlowExternalOperationTest() {

    @Test(timeout = 300_000)
    fun `external async operation`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            alice.rpc.startFlow(::FlowWithExternalAsyncOperation, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(1.minutes)
            assertHospitalCounters(0, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external async operation that checks deduplicationId is not rerun when flow is retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            assertFailsWith<DuplicatedProcessException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationWithDeduplication,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(1.minutes)
            }
            assertHospitalCounters(1, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external async operation propagates exception to calling flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationPropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    MyCordaException::class.java
                ).returnValue.getOrThrow(1.minutes)
            }
            assertHospitalCounters(0, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external async operation exception can be caught in flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            val result = alice.rpc.startFlow(
                ::FlowWithExternalAsyncOperationThatThrowsExceptionAndCaughtInFlow,
                bob.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(1.minutes)
            assertTrue(result as Boolean)
            assertHospitalCounters(0, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external async operation with exception that hospital keeps for observation does not fail`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            blockUntilFlowKeptInForObservation {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationPropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    HospitalizeFlowException::class.java
                )
            }
            assertHospitalCounters(0, 1)
        }
    }

    @Test(timeout = 300_000)
    fun `external async operation with exception that hospital discharges is retried and runs the future again`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            blockUntilFlowKeptInForObservation {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationPropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    SQLTransientConnectionException::class.java
                )
            }
            assertHospitalCounters(3, 1)
        }
    }

    @Test(timeout = 300_000)
    fun `external async operation that throws exception rather than completing future exceptionally fails with internal exception`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            assertFailsWith<StateTransitionException> {
                alice.rpc.startFlow(::FlowWithExternalAsyncOperationUnhandledException, bob.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(1.minutes)
            }
            assertHospitalCounters(0, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external async operation that passes serviceHub into process can be retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            blockUntilFlowKeptInForObservation {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationThatPassesInServiceHubCanRetry,
                    bob.nodeInfo.singleIdentity()
                )
            }
            assertHospitalCounters(3, 1)
        }
    }

    @Test(timeout = 300_000)
    fun `external async operation that accesses serviceHub from flow directly will fail when retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            assertFailsWith<DirectlyAccessedServiceHubException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalAsyncOperationThatDirectlyAccessesServiceHubFailsRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(1.minutes)
            }
            assertHospitalCounters(1, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `starting multiple futures and joining on their results`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            alice.rpc.startFlow(::FlowThatStartsMultipleFuturesAndJoins, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow(1.minutes)
            assertHospitalCounters(0, 0)
        }
    }

    @StartableByRPC
    class FlowWithExternalAsyncOperation(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any =
            await(ExternalAsyncOperation(serviceHub) { serviceHub, _ ->
                serviceHub.cordaService(FutureService::class.java).createFuture()
            })
    }

    @StartableByRPC
    class FlowWithExternalAsyncOperationPropagatesException<T>(party: Party, private val exceptionType: Class<T>) :
        FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any {
            val e = createException()
            return await(ExternalAsyncOperation(serviceHub) { _, _ ->
                CompletableFuture<Any>().apply {
                    completeExceptionally(e)
                }
            })
        }

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
package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowBackgroundProcess
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.flows.async
import net.corda.core.flows.blockingAsync
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doOnComplete
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateTransitionException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.junit.Test
import java.sql.SQLTransientConnectionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowBackgroundProcessTest {

    @Test
    fun `inline function works`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithBackgroundProcess, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline fork join works`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::ForkJoinProcesses, bob.nodeInfo.singleIdentity()).returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline function that throws exception is propagated up to the calling flow`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(
                    ::FlowWithBackgroundProcessThatThrowsException,
                    bob.nodeInfo.singleIdentity(),
                    MyCordaException::class.java
                ).returnValue.getOrThrow(20.seconds)
            }
            val (observation, discharged) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline function that throws exception can be caught and dealt with`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(
                ::FlowWithBackgroundProcessThatThrowsExceptionAndCaughtInFlow,
                bob.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(20.seconds)
            val (observation, discharged) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `inline function with exception that hospital keeps for observation does not fail`() {
        driver(DriverParameters(startNodesInProcess = false)) {
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
        driver(DriverParameters(startNodesInProcess = false)) {
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
    fun `manually instantiated background process works`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithBackgroundProcessThatIsManuallyDefined, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `manually instantiated background process propagates exception to calling flow`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(::FlowWithBackgroundProcessThatIsManuallyDefinedPropagatesException, bob.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `manually instantiated background process that throws exception rather than completing future exceptionally fails with internal exception`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<StateTransitionException> {
                alice.rpc.startFlow(::FlowWithBackgroundProcessThatIsManuallyDefinedUnhandledException, bob.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `starting a flow inside of a flow that starts a future will succeed`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowThatStartsAnotherFlowInABackgroundProcess, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(40.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `flows can be forked and joined from inside a flow`() {
        driver(DriverParameters(startNodesInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::ForkJoinFlows, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(40.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }
}

class MyCordaException(message: String) : CordaException(message)

class BackgroundProcess(private val function: () -> CordaFuture<Any>) : FlowBackgroundProcess<Any> {
    override fun execute(deduplicationId: String): CordaFuture<Any> {
        return function()
    }
}

@CordaService
class FutureService(private val services: AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val log = contextLogger()
    }

    private val executorService = Executors.newFixedThreadPool(8)

    fun createFuture(): CordaFuture<Any> = executorService.fork {
        log.info("Starting sleep inside of future")
        Thread.sleep(2000)
        log.info("Finished sleep inside of future")
        "Here is your return value"
    }

    fun createExceptionFuture(): CordaFuture<Any> = openFuture<Any>().apply {
        setException(MyCordaException("threw exception in background process"))
    }

    fun throwException(): Nothing = throw MyCordaException("threw exception in background process")

    fun forkJoinProcesses(): CordaFuture<List<Any>> = executorService.fork {
        log.info("Starting fork join")
        (1..5).map { createFuture().getOrThrow() }
    }

    fun startFlow(party: Party): CordaFuture<Any> = executorService.fork {
        log.info("Starting new flow")
        services.startFlow(FlowWithBackgroundProcess(party)).returnValue.doOnComplete { log.info("Finished new flow") }.get()
    }

    fun startFlows(party: Party): CordaFuture<List<Any>> = executorService.fork {
        log.info("Starting new flows")
        (1..5).map { i ->
            services.startFlow(FlowWithBackgroundProcess(party))
                .returnValue
                .doOnComplete { log.info("Finished new flow $i") }
                .getOrThrow()
        }
//            listOf(
//                services.startFlow(FlowWithBackgroundProcess(party)).returnValue.doOnComplete { log.info("Finished new flow 1") },
//                services.startFlow(FlowWithBackgroundProcess(party)).returnValue.doOnComplete { log.info("Finished new flow 2") },
//                services.startFlow(FlowWithBackgroundProcess(party)).returnValue.doOnComplete { log.info("Finished new flow 3") },
//                services.startFlow(FlowWithBackgroundProcess(party)).returnValue.doOnComplete { log.info("Finished new flow 4") },
//                services.startFlow(FlowWithBackgroundProcess(party)).returnValue.doOnComplete { log.info("Finished new flow 5") }
//            ).map { it.getOrThrow() }
    }
}

@StartableByRPC
@InitiatingFlow
@StartableByService
open class FlowWithBackgroundProcess(val party: Party) : FlowLogic<Any>() {

    companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call(): Any {
        log.info("Started my flow")
        val result = testCode()
        val session = initiateFlow(party)
        session.send("hi there")
        session.receive<String>()
        log.info("Finished my flow")
        return result
    }

    @Suspendable
    open fun testCode(): Any = blockingAsync { deduplicationId ->
        log.info("Inside of background process - $deduplicationId")
        "Background process completed - ($deduplicationId)"
    }
}

@StartableByRPC
class ForkJoinProcesses(party: Party) : FlowWithBackgroundProcess(party) {

    override fun testCode(): Any =
        async(serviceHub.cordaService(FutureService::class.java).forkJoinProcesses()).also { log.info("Result - $it") }
}

@StartableByRPC
class FlowWithBackgroundProcessThatThrowsException<T : Exception>(party: Party, private val exceptionType: Class<T>) :
    FlowWithBackgroundProcess(party) {

    override fun testCode(): Any = blockingAsync { deduplicationId ->
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
        blockingAsync { deduplicationId ->
            logger.info("Inside of background process")
            throw MyCordaException("boom")
        }
    } catch (e: MyCordaException) {
        log.info("Exception was caught")
        "Exception was caught"
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatIsManuallyDefined(party: Party) : FlowWithBackgroundProcess(party) {

    override fun testCode(): Any = async(BackgroundProcess { serviceHub.cordaService(FutureService::class.java).createFuture() })
}

@StartableByRPC
class FlowWithBackgroundProcessThatIsManuallyDefinedPropagatesException(party: Party) : FlowWithBackgroundProcess(party) {

    override fun testCode(): Any = async(BackgroundProcess { serviceHub.cordaService(FutureService::class.java).createExceptionFuture() })
}

@StartableByRPC
class FlowWithBackgroundProcessThatIsManuallyDefinedUnhandledException(party: Party) : FlowWithBackgroundProcess(party) {

    override fun testCode(): Any = async(BackgroundProcess { serviceHub.cordaService(FutureService::class.java).throwException() })
}

@StartableByRPC
class FlowThatStartsAnotherFlowInABackgroundProcess(party: Party) : FlowWithBackgroundProcess(party) {

    override fun testCode(): Any =
        async(serviceHub.cordaService(FutureService::class.java).startFlow(party)).also { log.info("Result - $it") }
}

@StartableByRPC
class ForkJoinFlows(party: Party) : FlowWithBackgroundProcess(party) {

    override fun testCode(): Any =
        async(serviceHub.cordaService(FutureService::class.java).startFlows(party)).also { log.info("Result - $it") }
}

@InitiatedBy(FlowWithBackgroundProcess::class)
class FlowWithBackgroundProcessResponder(val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        session.receive<String>()
        session.send("go away")
    }
}

@StartableByRPC
class GetHospitalCountersFlow : FlowLogic<HospitalCounts>() {
    override fun call(): HospitalCounts =
        HospitalCounts(
            serviceHub.cordaService(HospitalCounter::class.java).dischargeCounter,
            serviceHub.cordaService(HospitalCounter::class.java).observationCounter
        )
}

@CordaSerializable
data class HospitalCounts(val discharge: Int, val observation: Int)

@Suppress("UNUSED_PARAMETER")
@CordaService
class HospitalCounter(services: AppServiceHub) : SingletonSerializeAsToken() {
    var observationCounter: Int = 0
    var dischargeCounter: Int = 0

    init {
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++dischargeCounter }
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ -> ++observationCounter }
    }
}
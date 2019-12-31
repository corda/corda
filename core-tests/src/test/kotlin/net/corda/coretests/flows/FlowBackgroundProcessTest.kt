package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.flows.await
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doOnComplete
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.statemachine.StaffedFlowHospital
import java.sql.SQLTransientConnectionException
import java.util.concurrent.Executors

abstract class FlowBackgroundProcessTest

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
        log.info("ServiceHub value = $serviceHub")
        session.receive<String>()
        log.info("Finished my flow")
        return result
    }

    @Suspendable
    open fun testCode(): Any = await { _, deduplicationId ->
        log.info("Inside of background process - $deduplicationId")
        "Background process completed - ($deduplicationId)"
    }
}

@InitiatedBy(FlowWithBackgroundProcess::class)
class FlowWithBackgroundProcessResponder(val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        session.receive<String>()
        session.send("go away")
    }
}

@CordaService
class FutureService(private val services: AppServiceHub) : SingletonSerializeAsToken() {

    companion object {
        val log = contextLogger()
    }

    private val executorService = Executors.newFixedThreadPool(8)

    private val deduplicationIds = mutableSetOf<String>()

    fun createFuture(): CordaFuture<Any> = executorService.fork {
        log.info("Starting sleep inside of future")
        Thread.sleep(2000)
        log.info("Finished sleep inside of future")
        "Here is your return value"
    }

    fun createFutureWithDeduplication(deduplicationId: String): CordaFuture<Any> = executorService.fork {
        log.info("Creating future")
        if (deduplicationId !in deduplicationIds) {
            deduplicationIds += deduplicationId
            throw SQLTransientConnectionException("fake exception - connection is not available")
        }
        throw DuplicatedProcessException(deduplicationId)
    }

    fun throwHospitalHandledException(): Nothing = throw SQLTransientConnectionException("fake exception - connection is not available")

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
package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowExternalFuture
import net.corda.core.flows.FlowExternalResult
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doOnComplete
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.statemachine.StaffedFlowHospital
import java.sql.SQLTransientConnectionException
import java.util.concurrent.Executors
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

abstract class AbstractFlowExternalResultTest {

    @StartableByRPC
    @InitiatingFlow
    @StartableByService
    open class FlowWithExternalProcess(val party: Party) : FlowLogic<Any>() {

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
        open fun testCode(): Any = await(ExternalResult(serviceHub) { _, deduplicationId ->
            log.info("Inside of background process - $deduplicationId")
            "Background process completed - ($deduplicationId)"
        })
    }

    @InitiatedBy(FlowWithExternalProcess::class)
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

        fun createExceptionFutureWithDeduplication(deduplicationId: String): CordaFuture<Any> = executorService.fork {
            createExceptionWithDeduplication(deduplicationId)
        }

        fun createExceptionWithDeduplication(deduplicationId: String): Any {
            log.info("Creating future")
            if (deduplicationId !in deduplicationIds) {
                deduplicationIds += deduplicationId
                throw SQLTransientConnectionException("fake exception - connection is not available")
            }
            throw DuplicatedProcessException(deduplicationId)
        }

        fun setHospitalHandledException(): CordaFuture<Any> = openFuture<Any>().apply {
            setException(SQLTransientConnectionException("fake exception - connection is not available"))
        }

        fun throwHospitalHandledException(): Nothing = throw SQLTransientConnectionException("fake exception - connection is not available")

        fun startMultipleFuturesAndJoin(): CordaFuture<List<Any>> = executorService.fork {
            log.info("Creating multiple futures")
            (1..5).map { createFuture().getOrThrow() }
        }

        fun startFlow(party: Party): CordaFuture<Any> = executorService.fork {
            log.info("Starting new flow")
            services.startFlow(FlowWithExternalProcess(party)).returnValue.doOnComplete { log.info("Finished new flow") }.get()
        }

        fun startFlows(party: Party): CordaFuture<List<Any>> = executorService.fork {
            log.info("Starting new flows")
            (1..5).map { i ->
                services.startFlow(FlowWithExternalProcess(party))
                    .returnValue
                    .doOnComplete { log.info("Finished new flow $i") }
                    .getOrThrow()
            }
        }

        fun readFromDatabase(name: String): CustomTableEntity? = services.withEntityManager { find(CustomTableEntity::class.java, name) }

        fun saveToDatabaseWithEntityManager(entity: CustomTableEntity): Unit = services.withEntityManager {
            persist(entity)
        }

        fun saveToDatabaseWithJdbcSession(entity: CustomTableEntity): Unit = services.database.transaction {
            services.jdbcSession()
                .createStatement()
                .execute("INSERT INTO custom_table (name, quote) VALUES ('${entity.name}', '${entity.quote}');")
        }

        fun saveToDatabaseWithDatabaseTransaction(entity: CustomTableEntity): Unit = services.database.transaction {
            session.save(entity)
        }

        fun throwExceptionInsideOfDatabaseTransaction(): Nothing = services.database.transaction {
            throw SQLTransientConnectionException("connection is not available")
        }
    }

    @Entity
    @Table(name = "custom_table")
    data class CustomTableEntity constructor(
        @Id
        @Column(name = "name", nullable = false)
        var name: String,
        @Column(name = "quote", nullable = false)
        var quote: String
    )

    object CustomSchema

    object CustomMappedSchema : MappedSchema(CustomSchema::class.java, 1, listOf(CustomTableEntity::class.java))

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

    class MyCordaException(message: String) : CordaException(message)

    class DirectlyAccessedServiceHubException : CordaException("Null pointer from accessing flow's serviceHub")

    class DuplicatedProcessException(private val deduplicationId: String) : CordaException("Duplicated process: $deduplicationId")

    class ExternalResult<R : Any>(
        private val serviceHub: ServiceHub,
        private val operation: (serviceHub: ServiceHub, deduplicationId: String) -> R
    ) : FlowExternalResult<R> {
        override fun execute(deduplicationId: String): R {
            return operation(serviceHub, deduplicationId)
        }
    }

    class ExternalFuture<R : Any>(
        private val serviceHub: ServiceHub,
        private val function: (serviceHub: ServiceHub, deduplicationId: String) -> CordaFuture<R>
    ) : FlowExternalFuture<R> {
        override fun execute(deduplicationId: String): CordaFuture<R> {
            return function(serviceHub, deduplicationId)
        }
    }
}
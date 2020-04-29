package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaException
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.core.flows.FlowExternalOperation
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doOnComplete
import net.corda.core.messaging.FlowHandle
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.statemachine.StaffedFlowHospital
import org.junit.Before
import java.sql.SQLTransientConnectionException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.function.Supplier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import kotlin.test.assertEquals

abstract class AbstractFlowExternalOperationTest {

    var dischargeCounter = 0
    var observationCounter = 0

    @Before
    fun before() {
        StaffedFlowHospital.onFlowDischarged.clear()
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++dischargeCounter }
        StaffedFlowHospital.onFlowKeptForOvernightObservation.clear()
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ -> ++observationCounter }
        dischargeCounter = 0
        observationCounter = 0
    }

    fun blockUntilFlowKeptInForObservation(flow: () -> FlowHandle<*>) {
        val lock = Semaphore(0)
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ -> lock.release() }
        flow()
        lock.acquire()
    }

    fun assertHospitalCounters(discharge: Int, observation: Int) {
        assertEquals(discharge, dischargeCounter)
        assertEquals(observation, observationCounter)
    }

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
            subFlow(PingPongFlow(party))
            val result = testCode()
            val session = initiateFlow(party)
            session.sendAndReceive<String>("hi there").unwrap { it }
            session.sendAndReceive<String>("hi there").unwrap { it }
            subFlow(PingPongFlow(party))
            log.info("Finished my flow")
            return result
        }

        @Suspendable
        open fun testCode(): Any = await(ExternalOperation(serviceHub) { _, deduplicationId ->
            log.info("Inside of background process - $deduplicationId")
            "Background process completed - ($deduplicationId)"
        })
    }

    @InitiatedBy(FlowWithExternalProcess::class)
    class FlowWithExternalOperationResponder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            session.receive<String>().unwrap { it }
            session.send("go away")
            session.receive<String>().unwrap { it }
            session.send("go away")
        }
    }

    @InitiatingFlow
    class PingPongFlow(val party: Party): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(party)
            session.sendAndReceive<String>("ping pong").unwrap { it }
        }
    }

    @InitiatedBy(PingPongFlow::class)
    class PingPongResponder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            session.receive<String>().unwrap { it }
            session.send("I got you bro")
        }
    }

    @CordaService
    class FutureService(private val services: AppServiceHub) : SingletonSerializeAsToken() {

        companion object {
            val log = contextLogger()
        }

        private val executorService = Executors.newFixedThreadPool(8)

        private val deduplicationIds = mutableSetOf<String>()

        fun createFuture(): CompletableFuture<Any> {
            return CompletableFuture.supplyAsync(Supplier<Any> {
                log.info("Starting sleep inside of future")
                Thread.sleep(1000)
                log.info("Finished sleep inside of future")
                "Here is your return value"
            }, executorService)
        }

        fun createExceptionFutureWithDeduplication(deduplicationId: String): CompletableFuture<Any> {
            return CompletableFuture.supplyAsync(
                Supplier<Any> { createExceptionWithDeduplication(deduplicationId) },
                executorService
            )
        }

        fun createExceptionWithDeduplication(deduplicationId: String): Any {
            log.info("Creating future")
            if (deduplicationId !in deduplicationIds) {
                deduplicationIds += deduplicationId
                throw SQLTransientConnectionException("fake exception - connection is not available")
            }
            throw DuplicatedProcessException(deduplicationId)
        }

        fun setHospitalHandledException(): CompletableFuture<Any> = CompletableFuture<Any>().apply {
            completeExceptionally(SQLTransientConnectionException("fake exception - connection is not available"))
        }

        fun throwHospitalHandledException(): Nothing = throw SQLTransientConnectionException("fake exception - connection is not available")

        fun startMultipleFuturesAndJoin(): CompletableFuture<List<Any>> {
            return CompletableFuture.supplyAsync(
                Supplier<List<Any>> {
                    log.info("Creating multiple futures")
                    (1..5).map { createFuture().getOrThrow() }
                },
                executorService
            )
        }

        fun startFlow(party: Party): CompletableFuture<Any> {
            return CompletableFuture.supplyAsync(
                Supplier<Any> {
                    log.info("Starting new flow")
                    services.startFlow(FlowWithExternalProcess(party)).returnValue
                        .doOnComplete { log.info("Finished new flow") }.get()
                },
                executorService
            )
        }

        fun startFlows(party: Party): CompletableFuture<List<Any>> {
            return CompletableFuture.supplyAsync(
                Supplier<List<Any>> {
                    log.info("Starting new flows")
                    (1..5).map { i ->
                        services.startFlow(FlowWithExternalProcess(party))
                            .returnValue
                            .doOnComplete { log.info("Finished new flow $i") }
                            .getOrThrow()
                    }
                },
                executorService
            )
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

    class MyCordaException(message: String) : CordaException(message)

    class DirectlyAccessedServiceHubException : CordaException("Null pointer from accessing flow's serviceHub")

    class DuplicatedProcessException(private val deduplicationId: String) : CordaException("Duplicated process: $deduplicationId")

    class ExternalOperation<R : Any>(
        private val serviceHub: ServiceHub,
        private val operation: (serviceHub: ServiceHub, deduplicationId: String) -> R
    ) : FlowExternalOperation<R> {
        override fun execute(deduplicationId: String): R {
            return operation(serviceHub, deduplicationId)
        }
    }

    class ExternalAsyncOperation<R : Any>(
        private val serviceHub: ServiceHub,
        private val function: (serviceHub: ServiceHub, deduplicationId: String) -> CompletableFuture<R>
    ) : FlowExternalAsyncOperation<R> {
        override fun execute(deduplicationId: String): CompletableFuture<R> {
            return function(serviceHub, deduplicationId)
        }
    }
}
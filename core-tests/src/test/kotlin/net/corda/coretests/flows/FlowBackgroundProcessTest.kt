package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doOnComplete
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.queryBy
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.cordappsForPackages
import org.junit.Test
import java.sql.SQLTransientConnectionException
import java.util.concurrent.Executors
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowBackgroundProcessTest {

    @Test
    fun `vault can be queried`() {
        driver(
            DriverParameters(
                cordappsForAllNodes = cordappsForPackages(DummyState::class.packageName),
                startNodesInProcess = true
            )
        ) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithBackgroundProcessThatQueriesVault)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `data can be persisted to node database via entity manager`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithBackgroundProcessThatPersistsViaEntityManager)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `data can be persisted to node database via jdbc session`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithBackgroundProcessThatPersistsViaJdbcSession)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `data can be persisted to node database via servicehub database transaction`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithBackgroundProcessThatPersistsViaDatabaseTransaction)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `data can be persisted to node database in background process and read from another process once finished`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithBackgroundProcessThatPersistsToDatabaseAndReadsFromBackgroundProcess)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `background process can be retried when an error occurs inside of database transaction`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            val success = alice.rpc.startFlow(
                ::FlowWithBackgroundProcessThatErrorsInsideOfDatabaseTransaction,
                bob.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(20.seconds)
            assertTrue(success as Boolean)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatQueriesVault : FlowLogic<Boolean>() {

    @Suspendable
    override fun call(): Boolean {
        val state = DummyState(1, listOf(ourIdentity))
        val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
            addOutputState(state)
            addCommand(DummyContract.Commands.Create(), listOf(ourIdentity.owningKey))
        }
        val stx = serviceHub.signInitialTransaction(tx)
        serviceHub.recordTransactions(stx)
        return await { serviceHub, _ ->
            serviceHub.vaultService.queryBy<DummyState>().states.single().state.data == state
        }
    }
}

abstract class FlowWithBackgroundProcessThatPersistsToDatabase : FlowLogic<Boolean>() {

    @Suspendable
    override fun call(): Boolean {
        val (entityOne, entityTwo, entityThree) = saveToDatabase()
        return serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityOne.name) == entityOne &&
                serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityTwo.name) == entityTwo &&
                serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityThree.name) == entityThree
    }

    @Suspendable
    abstract fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity>
}

@StartableByRPC
class FlowWithBackgroundProcessThatPersistsViaEntityManager : FlowWithBackgroundProcessThatPersistsToDatabase() {

    @Suspendable
    override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
        val entityOne = CustomTableEntity("Darth Vader", "I find your lack of faith disturbing.")
        val entityTwo = CustomTableEntity("Obi-Wan Kenobi", "The Force will be with you. Always.")
        val entityThree = CustomTableEntity("Admiral Ackbar", "It’s a trap!")
        await { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityOne)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityTwo)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityThree)
        }
        return Triple(entityOne, entityTwo, entityThree)
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatPersistsViaJdbcSession : FlowWithBackgroundProcessThatPersistsToDatabase() {

    @Suspendable
    override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
        val entityOne = CustomTableEntity("Tony Stark", "I am Iron Man.")
        val entityTwo = CustomTableEntity("Captain America", "I can do this all day.")
        val entityThree = CustomTableEntity("Hulk", "Puny god.")
        await { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityOne)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityTwo)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityThree)
        }
        return Triple(entityOne, entityTwo, entityThree)
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatPersistsViaDatabaseTransaction : FlowWithBackgroundProcessThatPersistsToDatabase() {

    @Suspendable
    override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
        val entityOne = CustomTableEntity("Groot", "We are Groot.")
        val entityTwo = CustomTableEntity("Drax", "Nothing goes over my head. My reflexes are too fast. I would catch it.")
        val entityThree = CustomTableEntity("Doctor Strange", "Dormammu, I’ve come to bargain.")
        await { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityOne)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityTwo)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityThree)
        }
        return Triple(entityOne, entityTwo, entityThree)
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatPersistsToDatabaseAndReadsFromBackgroundProcess : FlowLogic<Boolean>() {

    @Suspendable
    override fun call(): Boolean {
        val entityOne = CustomTableEntity("Emperor Palpatine", "Now, young Skywalker, you will die.")
        val entityTwo = CustomTableEntity("Yoda", "My ally is the Force, and a powerful ally it is.")
        val entityThree = CustomTableEntity("Han Solo", "Never tell me the odds!")
        await { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityOne)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityTwo)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityThree)
        }
        return await { serviceHub, _ ->
            return@await serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityOne.name) == entityOne &&
                    serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityTwo.name) == entityTwo &&
                    serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityThree.name) == entityThree
        }
    }
}

@StartableByRPC
class FlowWithBackgroundProcessThatErrorsInsideOfDatabaseTransaction(party: Party) : FlowWithBackgroundProcess(party) {

    private companion object {
        var flag = false
    }

    @Suspendable
    override fun testCode(): Boolean {
        return await { serviceHub, _ ->
            if (!flag) {
                flag = true
                serviceHub.cordaService(FutureService::class.java).throwExceptionInsideOfDatabaseTransaction()
            } else {
                val entity = CustomTableEntity("Emperor Palpatine", "Now, young Skywalker, you will die.")
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entity)
                return@await serviceHub.cordaService(FutureService::class.java).readFromDatabase(entity.name) != null
            }
        }
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
        log.info("Creating multiple futures")
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

object Schema

object MappedSchema : MappedSchema(Schema::class.java, 1, listOf(CustomTableEntity::class.java))

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
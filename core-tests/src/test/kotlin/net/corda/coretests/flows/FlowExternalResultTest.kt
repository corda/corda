package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.coretests.flows.AbstractFlowExternalResultTest.CustomTableEntity
import net.corda.coretests.flows.AbstractFlowExternalResultTest.DirectlyAccessedServiceHubException
import net.corda.coretests.flows.AbstractFlowExternalResultTest.ExternalResult
import net.corda.coretests.flows.AbstractFlowExternalResultTest.FlowWithExternalProcess
import net.corda.coretests.flows.AbstractFlowExternalResultTest.FutureService
import net.corda.coretests.flows.AbstractFlowExternalResultTest.MyCordaException
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.cordappsForPackages
import org.junit.Test
import java.lang.IllegalStateException
import java.sql.SQLTransientConnectionException
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowExternalResultTest : AbstractFlowExternalResultTest() {

    @Test
    fun `external result`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithExternalResult, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external result that checks deduplicationId is not rerun when flow is retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<DuplicatedProcessException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalResultWithDeduplication,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external result propagates exception to calling flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalResultPropagatesException,
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
    fun `external result exception can be caught in flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            alice.rpc.startFlow(::FlowWithExternalResultThatThrowsExceptionAndCaughtInFlow, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(20.seconds)
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(0, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `external result with exception that hospital keeps for observation does not fail`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalResultPropagatesException,
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
    fun `external result with exception that hospital discharges is retried and runs the external operation again`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalResultPropagatesException,
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
    fun `external future that passes serviceHub into process can be retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalResultThatPassesInServiceHubCanRetry,
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
                    ::FlowWithExternalResultThatDirectlyAccessesServiceHubFailsRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(20.seconds)
            }
            val (discharged, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.getOrThrow()
            assertEquals(1, discharged)
            assertEquals(0, observation)
        }
    }

    @Test
    fun `vault can be queried`() {
        driver(
            DriverParameters(
                cordappsForAllNodes = cordappsForPackages(DummyState::class.packageName),
                startNodesInProcess = true
            )
        ) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithWithExternalResultThatQueriesVault)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `data can be persisted to node database via entity manager`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithExternalResultThatPersistsViaEntityManager)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `data can be persisted to node database via jdbc session`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithExternalResultThatPersistsViaJdbcSession)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `data can be persisted to node database via servicehub database transaction`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithExternalResultThatPersistsViaDatabaseTransaction)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `data can be persisted to node database in external operation and read from another process once finished`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithExternalResultThatPersistsToDatabaseAndReadsFromExternalResult)
                .returnValue.getOrThrow(20.seconds)
            assertTrue(success)
        }
    }

    @Test
    fun `external result can be retried when an error occurs inside of database transaction`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(providedName = BOB_NAME).getOrThrow()
            val success = alice.rpc.startFlow(
                ::FlowWithExternalResultThatErrorsInsideOfDatabaseTransaction,
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
class FlowWithExternalResult(party: Party) : FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any = await(ExternalResult(serviceHub) { _, _ -> "please return my message" })
}

@StartableByRPC
class FlowWithExternalResultWithDeduplication(party: Party) : FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any {
        return await(ExternalResult(serviceHub) { serviceHub, deduplicationId ->
            serviceHub.cordaService(FutureService::class.java).createExceptionWithDeduplication(deduplicationId)
        })
    }
}

@StartableByRPC
class FlowWithExternalResultPropagatesException<T>(party: Party, private val exceptionType: Class<T>) :
    FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any = await(ExternalResult(serviceHub) { _, _ -> throw createException() })

    private fun createException() = when (exceptionType) {
        HospitalizeFlowException::class.java -> HospitalizeFlowException("keep it around")
        SQLTransientConnectionException::class.java -> SQLTransientConnectionException("fake exception - connection is not available")
        else -> MyCordaException("boom")
    }
}

@StartableByRPC
class FlowWithExternalResultThatThrowsExceptionAndCaughtInFlow(party: Party) :
    FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any = try {
        await(ExternalResult(serviceHub) { _, _ ->
            throw IllegalStateException("threw exception in background process")
        })
    } catch (e: IllegalStateException) {
        log.info("Exception was caught")
        "Exception was caught"
    }
}

@StartableByRPC
class FlowWithExternalResultThatPassesInServiceHubCanRetry(party: Party) : FlowWithExternalProcess(party) {

    @Suspendable
    override fun testCode(): Any =
        await(ExternalResult(serviceHub) { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
        })
}

@StartableByRPC
class FlowWithExternalResultThatDirectlyAccessesServiceHubFailsRetry(party: Party) : FlowWithExternalProcess(party) {

    @Suppress("TooGenericExceptionCaught")
    @Suspendable
    override fun testCode(): Any {
        try {
            await(ExternalResult(serviceHub) { _, _ ->
                serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
            })
        } catch (e: NullPointerException) {
            throw DirectlyAccessedServiceHubException()
        }
    }
}

@StartableByRPC
class FlowWithWithExternalResultThatQueriesVault : FlowLogic<Boolean>() {

    @Suspendable
    override fun call(): Boolean {
        val state = DummyState(1, listOf(ourIdentity))
        val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
            addOutputState(state)
            addCommand(DummyContract.Commands.Create(), listOf(ourIdentity.owningKey))
        }
        val stx = serviceHub.signInitialTransaction(tx)
        serviceHub.recordTransactions(stx)
        return await(ExternalResult(serviceHub) { serviceHub, _ ->
            serviceHub.vaultService.queryBy<DummyState>().states.single().state.data == state
        })
    }
}

abstract class FlowWithExternalResultThatPersistsToDatabase : FlowLogic<Boolean>() {

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
class FlowWithExternalResultThatPersistsViaEntityManager : FlowWithExternalResultThatPersistsToDatabase() {

    @Suspendable
    override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
        val entityOne = CustomTableEntity("Darth Vader", "I find your lack of faith disturbing.")
        val entityTwo = CustomTableEntity("Obi-Wan Kenobi", "The Force will be with you. Always.")
        val entityThree = CustomTableEntity("Admiral Ackbar", "It’s a trap!")
        await(ExternalResult(serviceHub) { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityOne)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityTwo)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityThree)
        })
        return Triple(entityOne, entityTwo, entityThree)
    }
}

@StartableByRPC
class FlowWithExternalResultThatPersistsViaJdbcSession : FlowWithExternalResultThatPersistsToDatabase() {

    @Suspendable
    override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
        val entityOne = CustomTableEntity("Tony Stark", "I am Iron Man.")
        val entityTwo = CustomTableEntity("Captain America", "I can do this all day.")
        val entityThree = CustomTableEntity("Hulk", "Puny god.")
        await(ExternalResult(serviceHub) { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityOne)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityTwo)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityThree)
        })
        return Triple(entityOne, entityTwo, entityThree)
    }
}

@StartableByRPC
class FlowWithExternalResultThatPersistsViaDatabaseTransaction : FlowWithExternalResultThatPersistsToDatabase() {

    @Suspendable
    override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
        val entityOne = CustomTableEntity("Groot", "We are Groot.")
        val entityTwo = CustomTableEntity("Drax", "Nothing goes over my head. My reflexes are too fast. I would catch it.")
        val entityThree = CustomTableEntity("Doctor Strange", "Dormammu, I’ve come to bargain.")
        await(ExternalResult(serviceHub) { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityOne)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityTwo)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityThree)
        })
        return Triple(entityOne, entityTwo, entityThree)
    }
}

@StartableByRPC
class FlowWithExternalResultThatPersistsToDatabaseAndReadsFromExternalResult : FlowLogic<Boolean>() {

    @Suspendable
    override fun call(): Boolean {
        val entityOne = CustomTableEntity("Emperor Palpatine", "Now, young Skywalker, you will die.")
        val entityTwo = CustomTableEntity("Yoda", "My ally is the Force, and a powerful ally it is.")
        val entityThree = CustomTableEntity("Han Solo", "Never tell me the odds!")
        await(ExternalResult(serviceHub) { serviceHub, _ ->
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityOne)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityTwo)
            serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityThree)
        })
        return await(ExternalResult(serviceHub) { serviceHub, _ ->
            return@ExternalResult serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityOne.name) == entityOne &&
                    serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityTwo.name) == entityTwo &&
                    serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityThree.name) == entityThree
        })
    }
}

@StartableByRPC
class FlowWithExternalResultThatErrorsInsideOfDatabaseTransaction(party: Party) : FlowWithExternalProcess(party) {

    private companion object {
        var flag = false
    }

    @Suspendable
    override fun testCode(): Boolean {
        return await(ExternalResult(serviceHub) { serviceHub, _ ->
            if (!flag) {
                flag = true
                serviceHub.cordaService(FutureService::class.java).throwExceptionInsideOfDatabaseTransaction()
            } else {
                val entity = CustomTableEntity("Emperor Palpatine", "Now, young Skywalker, you will die.")
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entity)
                return@ExternalResult serviceHub.cordaService(FutureService::class.java).readFromDatabase(entity.name) != null
            }
        })
    }
}
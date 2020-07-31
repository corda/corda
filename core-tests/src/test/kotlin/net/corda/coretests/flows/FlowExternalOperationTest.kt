package net.corda.coretests.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowExternalOperationTest : AbstractFlowExternalOperationTest() {

    @Test(timeout = 300_000)
    fun `external operation`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            alice.rpc.startFlow(::FlowWithExternalOperation, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(1.minutes)
            assertHospitalCounters(0, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external operation that checks deduplicationId is not rerun when flow is retried`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            assertFailsWith<DuplicatedProcessException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalOperationWithDeduplication,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(1.minutes)
            }
            assertHospitalCounters(1, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external operation propagates exception to calling flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            assertFailsWith<MyCordaException> {
                alice.rpc.startFlow(
                    ::FlowWithExternalOperationPropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    MyCordaException::class.java
                ).returnValue.getOrThrow(1.minutes)
            }
            assertHospitalCounters(0, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external operation exception can be caught in flow`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            alice.rpc.startFlow(::FlowWithExternalOperationThatThrowsExceptionAndCaughtInFlow, bob.nodeInfo.singleIdentity())
                .returnValue.getOrThrow(1.minutes)
            assertHospitalCounters(0, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `external operation with exception that hospital keeps for observation does not fail`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            blockUntilFlowKeptInForObservation {
                alice.rpc.startFlow(
                    ::FlowWithExternalOperationPropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    HospitalizeFlowException::class.java
                )
            }
            assertHospitalCounters(0, 1)
        }
    }

    @Test(timeout = 300_000)
    fun `external operation with exception that hospital discharges is retried and runs the external operation again`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            blockUntilFlowKeptInForObservation {
                alice.rpc.startFlow(
                    ::FlowWithExternalOperationPropagatesException,
                    bob.nodeInfo.singleIdentity(),
                    SQLTransientConnectionException::class.java
                )
            }
            assertHospitalCounters(3, 1)
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
                    ::FlowWithExternalOperationThatPassesInServiceHubCanRetry,
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
                    ::FlowWithExternalOperationThatDirectlyAccessesServiceHubFailsRetry,
                    bob.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(1.minutes)
            }
            assertHospitalCounters(1, 0)
        }
    }

    @Test(timeout = 300_000)
    fun `vault can be queried`() {
        driver(
            DriverParameters(
                cordappsForAllNodes = cordappsForPackages(DummyState::class.packageName),
                startNodesInProcess = true
            )
        ) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithWithExternalOperationThatQueriesVault)
                .returnValue.getOrThrow(1.minutes)
            assertTrue(success)
        }
    }

    @Test(timeout = 300_000)
    fun `data can be persisted to node database via entity manager`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithExternalOperationThatPersistsViaEntityManager)
                .returnValue.getOrThrow(1.minutes)
            assertTrue(success)
        }
    }

    @Test(timeout = 300_000)
    fun `data can be persisted to node database via jdbc session`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithExternalOperationThatPersistsViaJdbcSession)
                .returnValue.getOrThrow(1.minutes)
            assertTrue(success)
        }
    }

    @Test(timeout = 300_000)
    fun `data can be persisted to node database via servicehub database transaction`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithExternalOperationThatPersistsViaDatabaseTransaction)
                .returnValue.getOrThrow(1.minutes)
            assertTrue(success)
        }
    }

    @Test(timeout = 300_000)
    fun `data can be persisted to node database in external operation and read from another process once finished`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val success = alice.rpc.startFlow(::FlowWithExternalOperationThatPersistsToDatabaseAndReadsFromExternalOperation)
                .returnValue.getOrThrow(1.minutes)
            assertTrue(success)
        }
    }

    @Test(timeout = 300_000)
    fun `external operation can be retried when an error occurs inside of database transaction`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val (alice, bob) = listOf(ALICE_NAME, BOB_NAME)
                    .map { startNode(providedName = it) }
                    .transpose()
                    .getOrThrow()
            val success = alice.rpc.startFlow(
                ::FlowWithExternalOperationThatErrorsInsideOfDatabaseTransaction,
                bob.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(1.minutes)
            assertTrue(success as Boolean)
            assertHospitalCounters(1, 0)
        }
    }

    @StartableByRPC
    class FlowWithExternalOperation(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any = await(ExternalOperation(serviceHub) { _, _ -> "please return my message" })
    }

    @StartableByRPC
    class FlowWithExternalOperationWithDeduplication(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any {
            return await(ExternalOperation(serviceHub) { serviceHub, deduplicationId ->
                serviceHub.cordaService(FutureService::class.java).createExceptionWithDeduplication(deduplicationId)
            })
        }
    }

    @StartableByRPC
    class FlowWithExternalOperationPropagatesException<T>(party: Party, private val exceptionType: Class<T>) :
        FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode() {
            val e = createException()
            await(ExternalOperation(serviceHub) { _, _ -> throw e })
        }

        private fun createException() = when (exceptionType) {
            HospitalizeFlowException::class.java -> HospitalizeFlowException("keep it around")
            SQLTransientConnectionException::class.java -> SQLTransientConnectionException("fake exception - connection is not available")
            else -> MyCordaException("boom")
        }
    }

    @StartableByRPC
    class FlowWithExternalOperationThatThrowsExceptionAndCaughtInFlow(party: Party) :
        FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any = try {
            await(ExternalOperation(serviceHub) { _, _ ->
                throw IllegalStateException("threw exception in background process")
            })
        } catch (e: IllegalStateException) {
            log.info("Exception was caught")
            "Exception was caught"
        }
    }

    @StartableByRPC
    class FlowWithExternalOperationThatPassesInServiceHubCanRetry(party: Party) : FlowWithExternalProcess(party) {

        @Suspendable
        override fun testCode(): Any =
            await(ExternalOperation(serviceHub) { serviceHub, _ ->
                serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
            })
    }

    @StartableByRPC
    class FlowWithExternalOperationThatDirectlyAccessesServiceHubFailsRetry(party: Party) : FlowWithExternalProcess(party) {

        @Suppress("TooGenericExceptionCaught")
        @Suspendable
        override fun testCode(): Any {
            try {
                await(ExternalOperation(serviceHub) { _, _ ->
                    serviceHub.cordaService(FutureService::class.java).throwHospitalHandledException()
                })
            } catch (e: NullPointerException) {
                throw DirectlyAccessedServiceHubException()
            }
        }
    }

    @StartableByRPC
    class FlowWithWithExternalOperationThatQueriesVault : FlowLogic<Boolean>() {

        @Suspendable
        override fun call(): Boolean {
            val state = DummyState(1, listOf(ourIdentity))
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addOutputState(state)
                addCommand(DummyContract.Commands.Create(), listOf(ourIdentity.owningKey))
            }
            val stx = serviceHub.signInitialTransaction(tx)
            serviceHub.recordTransactions(stx)
            return await(ExternalOperation(serviceHub) { serviceHub, _ ->
                serviceHub.vaultService.queryBy<DummyState>().states.single().state.data == state
            })
        }
    }

    abstract class FlowWithExternalOperationThatPersistsToDatabase : FlowLogic<Boolean>() {

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
    class FlowWithExternalOperationThatPersistsViaEntityManager : FlowWithExternalOperationThatPersistsToDatabase() {

        @Suspendable
        override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
            val entityOne = CustomTableEntity("Darth Vader", "I find your lack of faith disturbing.")
            val entityTwo = CustomTableEntity("Obi-Wan Kenobi", "The Force will be with you. Always.")
            val entityThree = CustomTableEntity("Admiral Ackbar", "It’s a trap!")
            await(ExternalOperation(serviceHub) { serviceHub, _ ->
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityOne)
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityTwo)
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityThree)
            })
            return Triple(entityOne, entityTwo, entityThree)
        }
    }

    @StartableByRPC
    class FlowWithExternalOperationThatPersistsViaJdbcSession : FlowWithExternalOperationThatPersistsToDatabase() {

        @Suspendable
        override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
            val entityOne = CustomTableEntity("Tony Stark", "I am Iron Man.")
            val entityTwo = CustomTableEntity("Captain America", "I can do this all day.")
            val entityThree = CustomTableEntity("Hulk", "Puny god.")
            await(ExternalOperation(serviceHub) { serviceHub, _ ->
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityOne)
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityTwo)
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityThree)
            })
            return Triple(entityOne, entityTwo, entityThree)
        }
    }

    @StartableByRPC
    class FlowWithExternalOperationThatPersistsViaDatabaseTransaction : FlowWithExternalOperationThatPersistsToDatabase() {

        @Suspendable
        override fun saveToDatabase(): Triple<CustomTableEntity, CustomTableEntity, CustomTableEntity> {
            val entityOne = CustomTableEntity("Groot", "We are Groot.")
            val entityTwo = CustomTableEntity("Drax", "Nothing goes over my head. My reflexes are too fast. I would catch it.")
            val entityThree = CustomTableEntity("Doctor Strange", "Dormammu, I’ve come to bargain.")
            await(ExternalOperation(serviceHub) { serviceHub, _ ->
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityOne)
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityTwo)
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityThree)
            })
            return Triple(entityOne, entityTwo, entityThree)
        }
    }

    @StartableByRPC
    class FlowWithExternalOperationThatPersistsToDatabaseAndReadsFromExternalOperation : FlowLogic<Boolean>() {

        @Suspendable
        override fun call(): Boolean {
            val entityOne = CustomTableEntity("Emperor Palpatine", "Now, young Skywalker, you will die.")
            val entityTwo = CustomTableEntity("Yoda", "My ally is the Force, and a powerful ally it is.")
            val entityThree = CustomTableEntity("Han Solo", "Never tell me the odds!")
            await(ExternalOperation(serviceHub) { serviceHub, _ ->
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithEntityManager(entityOne)
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithJdbcSession(entityTwo)
                serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entityThree)
            })
            return await(ExternalOperation(serviceHub) { serviceHub, _ ->
                return@ExternalOperation serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityOne.name) == entityOne &&
                        serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityTwo.name) == entityTwo &&
                        serviceHub.cordaService(FutureService::class.java).readFromDatabase(entityThree.name) == entityThree
            })
        }
    }

    @StartableByRPC
    class FlowWithExternalOperationThatErrorsInsideOfDatabaseTransaction(party: Party) : FlowWithExternalProcess(party) {

        private companion object {
            var flag = false
        }

        @Suspendable
        override fun testCode(): Boolean {
            return await(ExternalOperation(serviceHub) { serviceHub, _ ->
                if (!flag) {
                    flag = true
                    serviceHub.cordaService(FutureService::class.java).throwExceptionInsideOfDatabaseTransaction()
                } else {
                    val entity = CustomTableEntity("Emperor Palpatine", "Now, young Skywalker, you will die.")
                    serviceHub.cordaService(FutureService::class.java).saveToDatabaseWithDatabaseTransaction(entity)
                    return@ExternalOperation serviceHub.cordaService(FutureService::class.java).readFromDatabase(entity.name) != null
                }
            })
        }
    }
}
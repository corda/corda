package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.nodeapi.internal.persistence.RolledBackDatabaseSessionException
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.hibernate.exception.ConstraintViolationException
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.sql.Savepoint
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.PersistenceException
import javax.persistence.Table
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests that I need
 *
 * 1)   No database error works (data saved) ✅
 * 2)   Constraint violation without a flush breaks (no data saved and savepoint released) ✅
 * 3)   Constraint violation with a flush breaks (no data saved and savepoint released) ✅
 * 4)   Constraint violation with a flush that is caught works (no data saved) ✅
 * 5)   Constraint violation with a flush that is caught outside of the entity manager block works (no data saved) ✅
 * 6)   Constraint violation on a single entity when saving multiple entities breaks ✅
 *      (no data saved / test transactionality within an entity manager block)
 * 7)   Constraint violation on a single entity when saving multiple entities breaks works ✅
 *      (no data saved / test transactionality within an entity manager block)
 * 8)   Constraint violation with a flush that is caught inside an entity manager and more data is saved afterwards in a new entity manager (the extra data should be saved) ✅
 * 9)   Constraint violation with a flush that is caught inside an entity manager and more data is saved afterwards in the same entity manager (throws exception) ✅
 * 10)  Constraint violation with a flush that is caught outside an entity manager and more data is saved afterwards in a new entity manager (the extra data should be saved) ✅
 * 11)  All data is saved when a suspension point is reached
 * 12)  Hibernate session is cleared when rolling back a session/save-point (calling find/merge/persist should not hit evicted entity)
 * 13)  Other types of hibernate ([PersistenceException]s) can be caught
 * 14)
 *
 * I should take into account having a commit in between the entity managers or not
 * Basically the tests above can be repeated with a commit between each entity manager (use parameterized tests that determine whether to
 * trigger the commit?)
 *
 * I should also test constraint violations within a single entity manager
 *
 * entity manager inside an entity manager?
 */
@RunWith(Parameterized::class)
class FlowEntityManagerTest(var commitStatus: CommitStatus) {

    private companion object {
        val entityWithIdOne = CustomTableEntity(1, "Dan", "This won't work")
        val anotherEntityWithIdOne = CustomTableEntity(1, "Rick", "I'm pretty sure this will work")
        val entityWithIdTwo = CustomTableEntity(2, "Ivan", "This will break existing CorDapps")
        val entityWithIdThree = CustomTableEntity(3, "Some other guy", "What am I doing here?")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(CommitStatus.INTERMEDIATE_COMMIT, CommitStatus.NO_INTERMEDIATE_COMMIT)
    }

    @CordaSerializable
    enum class CommitStatus { INTERMEDIATE_COMMIT, NO_INTERMEDIATE_COMMIT }

    @Before
    fun before() {
        StaffedFlowHospital.onFlowDischarged.clear()
    }

    // This doesn't need to be parameterized...
    @Test(timeout = 300_000)
    fun `entities can be saved using entity manager without a flush`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::EntityManagerSaveEntitiesWithoutAFlushFlow)
                    .returnValue.getOrThrow(Duration.of(10, ChronoUnit.SECONDS))
                assertEquals(0, counter)
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(3, entities.size)
            }
        }
    }

    // This doesn't need to be parameterized...
    @Test(timeout = 300_000)
    fun `entities can be saved using entity manager with a flush`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::EntityManagerSaveEntitiesWithAFlushFlow)
                    .returnValue.getOrThrow(Duration.of(10, ChronoUnit.SECONDS))
                assertEquals(0, counter)
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(3, entities.size)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation without a flush breaks`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                assertFailsWith<TimeoutException> {
                    it.proxy.startFlow(::EntityManagerErrorWithoutAFlushFlow, commitStatus)
                        .returnValue.getOrThrow(Duration.of(10, ChronoUnit.SECONDS))
                }
                assertEquals(3, counter)
                // 1 entity exists to trigger constraint violation
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(1, entities.size)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation with a flush breaks`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                assertFailsWith<TimeoutException> {
                    it.proxy.startFlow(::EntityManagerErrorWithAFlushFlow, commitStatus)
                        .returnValue.getOrThrow(Duration.of(10, ChronoUnit.SECONDS))
                }
                assertEquals(3, counter)
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(1, entities.size)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation with a flush that is caught inside an entity manager block saves no data`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::EntityManagerWithAFlushCatchErrorInsideTheEntityManagerFlow, commitStatus)
                    .returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                // 1 entity exists to trigger constraint violation
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(1, entities.size)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation with a flush that is caught outside the entity manager block saves no data`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(::EntityManagerWithAFlushCatchErrorOutsideTheEntityManagerFlow, commitStatus)
                    .returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                // 1 entity exists to trigger constraint violation
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(1, entities.size)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation on a single entity when saving multiple entities does not save any entities`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                assertFailsWith<TimeoutException> {
                    it.proxy.startFlow(::EntityManagerSavingMultipleEntitiesWithASingleErrorFlow, commitStatus)
                        .returnValue.getOrThrow(20.seconds)
                }
                assertEquals(3, counter)
                // 1 entity exists to trigger constraint violation
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(1, entities.size)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation on a single entity when saving multiple entities and catching the error does not save any entities`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::EntityManagerSavingMultipleEntitiesWithASingleCaughtErrorFlow,
                    commitStatus
                ).returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                // 1 entity exists to trigger constraint violation
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(1, entities.size)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation that is caught inside an entity manager and more data is saved afterwards inside a new entity manager should save the extra data`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::EntityManagerCatchErrorAndSaveMoreEntitiesInANewEntityManager,
                    commitStatus
                ).returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                // 1 entity exists to trigger constraint violation
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(3, entities.size)
            }
        }
    }

    // maybe we should throw an error if a session is broken but the flow tries to insert more entities
    @Test(timeout = 300_000)
    fun `constraint violation that is caught inside an entity manager and more data is saved afterwards inside the same entity manager should throw an exception`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                assertFailsWith<RolledBackDatabaseSessionException> {
                    it.proxy.startFlow(
                        ::EntityManagerCatchErrorAndSaveMoreEntitiesInTheSameEntityManager,
                        commitStatus
                    ).returnValue.getOrThrow(20.seconds)
                }
                assertEquals(0, counter)
                // 1 entity exists to trigger constraint violation
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                when (commitStatus) {
                    CommitStatus.INTERMEDIATE_COMMIT -> assertEquals(1, entities.size)
                    CommitStatus.NO_INTERMEDIATE_COMMIT -> assertEquals(0, entities.size)
                }
            }
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation that is caught outside an entity manager and more data is saved afterwards inside a new entity manager should save the extra data`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                it.proxy.startFlow(
                    ::EntityManagerCatchErrorOutsideTheEntityManagerAndSaveMoreEntitiesInANewEntityManager,
                    commitStatus
                ).returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                // 1 entity exists to trigger constraint violation
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(3, entities.size)
            }
        }
    }

    /*
    Once a flush is made and caught, it is not possible to do anything using [withEntityManager] again (even inside a new call to it).

    The question is, why is the flow able to progress as normal after catching a [flush] error but calling [withEntityManager] again
    fails due to the caught error resurfacing due to a [flush]. It does not matter whether the [flush] comes from a user [flush] or the
    [flush] that occurs when opening a [withEntityManager] block.
     */
    @Test(timeout = 300_000)
    @Ignore
    fun `constraint violation inside entity manager *with multiple flushes that insert extra data* and *catching the exception* will allow the flow to finish`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                val txId = it.proxy.startFlow(::EntityManagerWithFlushCatchAndNewDataFlow, nodeBHandle.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                val txFromVault = it.proxy.stateMachineRecordedTransactionMappingSnapshot().firstOrNull()?.transactionId
                assertEquals(txId, txFromVault)
                val entity = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow().single()
                assertEquals(entityWithIdOne, entity)
            }
        }
        assertEquals(0, counter)
    }

    @Test(timeout = 300_000)
    @Ignore
    fun `constraint violation inside entity manager *with multiple flushes that insert extra data* and *catching the exception* will allow the flow to finish 123123`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                val txId = it.proxy.startFlow(::EntityManagerWithFlushCatchAndNewDataFlow2, nodeBHandle.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                val txFromVault = it.proxy.stateMachineRecordedTransactionMappingSnapshot().firstOrNull()?.transactionId
                assertEquals(txId, txFromVault)
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(3, entities.size)
//                assertEquals(entityOne, entity)
            }
        }
        assertEquals(0, counter)
    }

    @Test(timeout = 300_000)
    @Ignore
    fun `constraint violation inside entity manager *with multiple flushes that insert extra data* and *catching the exception* will allow the flow to finish SAVE POINTS`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                val txId = it.proxy.startFlow(::EntityManagerWithFlushCatchAndNewDataFlow3, nodeBHandle.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                val txFromVault = it.proxy.stateMachineRecordedTransactionMappingSnapshot().firstOrNull()?.transactionId
                assertEquals(txId, txFromVault)
                val entities = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow()
                assertEquals(2, entities.size)
//                assertEquals(entityOne, entity)
            }
        }
        assertEquals(0, counter)
    }

    /*
    Calling rollback inside a flow blows it up when it reaches the next suspend/commit

    Caused by: java.lang.IllegalStateException: Transaction not successfully started
	at org.hibernate.engine.transaction.internal.TransactionImpl.commit(TransactionImpl.java:98) ~[hibernate-core-5.4.3.Final.jar:5.4.3.Final]
	at net.corda.nodeapi.internal.persistence.DatabaseTransaction.commit(DatabaseTransaction.kt:76) ~[corda-node-api-4.4-SNAPSHOT.jar:?]
	at net.corda.node.services.statemachine.ActionExecutorImpl.executeCommitTransaction(ActionExecutorImpl.kt:230) ~[corda-node-4.4-SNAPSHOT.jar:?]
	at net.corda.node.services.statemachine.ActionExecutorImpl.executeAction(ActionExecutorImpl.kt:77) ~[corda-node-4.4-SNAPSHOT.jar:?]
	at net.corda.node.services.statemachine.TransitionExecutorImpl.executeTransition(TransitionExecutorImpl.kt:44) ~[corda-node-4.4-SNAPSHOT.jar:?]
	... 18 more

     */
    @Test(timeout = 300_000)
    @Ignore
    fun `constraint violation inside entity manager *with flush* error that is caught, transaction is rolled back and new data is inserted, flow finishes`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true)) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {
                val txId = it.proxy.startFlow(::EntityManagerWithFlushCatchRollbackAndNewDataFlow, nodeBHandle.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(20.seconds)
                assertEquals(0, counter)
                val txFromVault = it.proxy.stateMachineRecordedTransactionMappingSnapshot().firstOrNull()?.transactionId
                assertEquals(txId, txFromVault)
                val entity = it.proxy.startFlow(::GetCustomEntities).returnValue.getOrThrow().single()
                assertEquals(entityWithIdOne, entity)
            }
        }
        assertEquals(0, counter)
    }

    @StartableByRPC
    class EntityManagerSaveEntitiesWithoutAFlushFlow : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
                persist(entityWithIdTwo)
                persist(entityWithIdThree)
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerSaveEntitiesWithAFlushFlow : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
                persist(entityWithIdTwo)
                persist(entityWithIdThree)
                flush()
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerErrorWithoutAFlushFlow(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(anotherEntityWithIdOne)
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerErrorWithAFlushFlow(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(anotherEntityWithIdOne)
                flush()
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerWithAFlushCatchErrorInsideTheEntityManagerFlow(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(anotherEntityWithIdOne)
                try {
                    flush()
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerWithAFlushCatchErrorOutsideTheEntityManagerFlow(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            try {
                serviceHub.withEntityManager {
                    persist(anotherEntityWithIdOne)
                    flush()
                }
            } catch (e: PersistenceException) {
                logger.info("Caught the exception!")
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerSavingMultipleEntitiesWithASingleErrorFlow(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(anotherEntityWithIdOne)
                persist(entityWithIdTwo)
                persist(entityWithIdThree)
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerSavingMultipleEntitiesWithASingleCaughtErrorFlow(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(anotherEntityWithIdOne)
                persist(entityWithIdTwo)
                persist(entityWithIdThree)
                try {
                    flush()
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerCatchErrorAndSaveMoreEntitiesInANewEntityManager(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(anotherEntityWithIdOne)
                try {
                    flush()
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(entityWithIdTwo)
                persist(entityWithIdThree)
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerCatchErrorAndSaveMoreEntitiesInTheSameEntityManager(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(anotherEntityWithIdOne)
                try {
                    flush()
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
                persist(entityWithIdTwo)
                persist(entityWithIdThree)
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerCatchErrorOutsideTheEntityManagerAndSaveMoreEntitiesInANewEntityManager(private val commitStatus: CommitStatus) :
        FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            try {
                serviceHub.withEntityManager {
                    persist(anotherEntityWithIdOne)
                    flush()
                }
            } catch (e: PersistenceException) {
                logger.info("Caught the exception!")
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                persist(entityWithIdTwo)
                persist(entityWithIdThree)
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerWithFlushCatchAndNewDataFlow(private val party: Party) : FlowLogic<SecureHash>() {
        companion object {
            val log = contextLogger()
        }

        @Suspendable
        override fun call(): SecureHash {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
                log.info("After the first insert")
            }
            sleep(1.millis)
            serviceHub.withEntityManager {
                // the exception is not triggered until the second commit
                // it does not happen inline by hibernate
                persist(anotherEntityWithIdOne)
                try {
                    flush()
                } catch (e: PersistenceException) {
                    // it does not make it to here as a constraint violation exception
                    if (e.cause is ConstraintViolationException) {
                        log.info("Caught the exception!")
                        clear()
                        detach(anotherEntityWithIdOne)
                    }
                }
            }
            serviceHub.withEntityManager {
                persist(entityWithIdTwo)
//                flush()
                log.info("After the second flush")
            }
            return subFlow(CreateATransactionFlow(party)).also {
                log.info("Reached the end of the flow")
            }
        }
    }

    @StartableByRPC
    class EntityManagerWithFlushCatchAndNewDataFlow2(private val party: Party) : FlowLogic<SecureHash>() {
        companion object {
            val log = contextLogger()
        }

        @Suspendable
        override fun call(): SecureHash {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
//                flush()
                log.info("After the first insert")
            }
            sleep(1.millis)
            doStuff()
            sleep(1.millis)
//            serviceHub.jdbcSession().setSavepoint()
            serviceHub.withEntityManager {
                val entity = find(CustomTableEntity::class.java, 1)
                log.info("I found the entity : $entity")
                val entity2 = find(CustomTableEntity::class.java, 2)
                log.info("I found the entity2 : $entity2")
                persist(entityWithIdThree)
                val entity3 = find(CustomTableEntity::class.java, 3)
                log.info("I found the entity3 : $entity3")
//                flush()
                log.info("After the second flush")
            }
            return subFlow(CreateATransactionFlow(party)).also {
                log.info("Reached the end of the flow")
            }
        }

        private fun doStuff() {
//            val savePoint = createSavepoint()
            serviceHub.withEntityManager {
                // the exception is not triggered until the second commit
                // it does not happen inline by hibernate
                persist(entityWithIdTwo)
                persist(anotherEntityWithIdOne)
                try {
                    flush()
                } catch (e: PersistenceException) {
                    // it does not make it to here as a constraint violation exception
                    if (e.cause is ConstraintViolationException) {
                        log.info("Caught the exception!")
//                        savePoint.rollback()
//                        clear()
//                        detach(entityTwo)
                    }
                }
            }
//            savePoint.release()
        }

        private fun createSavepoint(): Savepoint {
            val connection = serviceHub.jdbcSession()
            return connection.setSavepoint()
        }

        private fun Savepoint.rollback() = serviceHub.jdbcSession().rollback(this)

        private fun Savepoint.release() = serviceHub.jdbcSession().releaseSavepoint(this)
    }

    @StartableByRPC
    class EntityManagerWithFlushCatchAndNewDataFlow3(private val party: Party) : FlowLogic<SecureHash>() {
        companion object {
            val log = contextLogger()
        }

        @Suspendable
        override fun call(): SecureHash {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
//                flush()
                log.info("After the first insert")
            }
            sleep(1.millis)
            doStuff()
//            sleep(1.millis)
//            serviceHub.jdbcSession().setSavepoint()
            serviceHub.withEntityManager {
                val entity = find(CustomTableEntity::class.java, 1)
                log.info("I found the entity : $entity")
                val entity2 = find(CustomTableEntity::class.java, 2)
                log.info("I found the entity2 : $entity2")
                persist(entityWithIdThree)
                val entity3 = find(CustomTableEntity::class.java, 3)
                log.info("I found the entity3 : $entity3")
//                flush()
                log.info("After the second flush")
            }
            return subFlow(CreateATransactionFlow(party)).also {
                log.info("Reached the end of the flow")
            }
        }

        private fun doStuff() {
            val savePoint = createSavepoint()
            serviceHub.withEntityManager {
                // the exception is not triggered until the second commit
                // it does not happen inline by hibernate
                persist(entityWithIdTwo)
                persist(anotherEntityWithIdOne)
                try {
                    flush()
                } catch (e: PersistenceException) {
                    // it does not make it to here as a constraint violation exception
                    if (e.cause is ConstraintViolationException) {
                        log.info("Caught the exception!")
                        savePoint.rollback()
//                        clear()
//                        detach(entityTwo)
                    }
                }
            }
            savePoint.release()
        }

        private fun createSavepoint(): Savepoint {
            val connection = serviceHub.jdbcSession()
            return connection.setSavepoint()
        }

        private fun Savepoint.rollback() = serviceHub.jdbcSession().rollback(this)

        private fun Savepoint.release() = serviceHub.jdbcSession().releaseSavepoint(this)
    }

    @StartableByRPC
    class EntityManagerWithFlushCatchRollbackAndNewDataFlow(private val party: Party) : FlowLogic<SecureHash>() {
        companion object {
            val log = contextLogger()
        }

        @Suspendable
        override fun call(): SecureHash {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
                log.info("After the first insert")
            }
            sleep(1.millis)
            serviceHub.withEntityManager {
                // the exception is not triggered until the second commit
                // it does not happen inline by hibernate
                persist(anotherEntityWithIdOne)
                try {
                    flush()
                } catch (e: PersistenceException) {
                    // it does not make it to here as a constraint violation exception
                    if (e.cause is ConstraintViolationException) {
                        log.info("Caught the exception!")
                        transaction.rollback()
                        transaction.begin()
                    }
                }
            }
            serviceHub.withEntityManager {
                persist(entityWithIdTwo)
                flush()
                log.info("After the second flush")
            }
            return subFlow(CreateATransactionFlow(party)).also {
                log.info("Reached the end of the flow")
            }
        }
    }

    @InitiatingFlow
    class CreateATransactionFlow(val party: Party) : FlowLogic<SecureHash>() {
        @Suspendable
        override fun call(): SecureHash {
            val session = initiateFlow(party)
            val tx = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first()).apply {
                addOutputState(DummyState(participants = listOf(ourIdentity, party)))
                addCommand(DummyCommandData, ourIdentity.owningKey, party.owningKey)
            }
            val stx = serviceHub.signInitialTransaction(tx)
            val ftx = subFlow(CollectSignaturesFlow(stx, listOf(session)))
            return subFlow(FinalityFlow(ftx, session)).id
        }
    }

    @InitiatedBy(CreateATransactionFlow::class)
    class CreateATransactionResponder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val stx = subFlow(object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                }
            })
            subFlow(ReceiveFinalityFlow(session, stx.id))
        }
    }

    @StartableByRPC
    class GetCustomEntities : FlowLogic<List<CustomTableEntity>>() {
        @Suspendable
        override fun call(): List<CustomTableEntity> {
            return serviceHub.withEntityManager {
                val criteria = criteriaBuilder.createQuery(CustomTableEntity::class.java)
                criteria.select(criteria.from(CustomTableEntity::class.java))
                createQuery(criteria).resultList.also {
                    logger.info("results = $it")
                }
            }
        }
    }

    @InitiatingFlow
    class PingPongFlow(val party: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(party)
            session.sendAndReceive<String>("ping pong").unwrap { it }
            logger.info("Finished the ping pong flow")
        }
    }

    @InitiatedBy(PingPongFlow::class)
    class PingPongResponder(val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            session.receive<String>().unwrap { it }
            session.send("I got you bro")
            logger.info("Finished the ping pong responder")
        }
    }

    @Entity
    @Table(name = "custom_table")
    @CordaSerializable
    data class CustomTableEntity constructor(
        @Id
        @Column(name = "id", nullable = false)
        var id: Int,
        @Column(name = "name", nullable = false)
        var name: String,
        @Column(name = "quote", nullable = false)
        var quote: String
    )

    object CustomSchema

    object CustomMappedSchema : MappedSchema(CustomSchema::class.java, 1, listOf(CustomTableEntity::class.java))
}
package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.hibernate.exception.ConstraintViolationException
import org.junit.Test
import javax.persistence.PersistenceException
import kotlin.test.assertEquals

class FlowEntityManagerStatementTest : AbstractFlowEntityManagerTest() {

    @Test(timeout = 300_000)
    fun `data can be saved by a sql statement using entity manager`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()

            alice.rpc.startFlow(::EntityManagerSqlFlow).returnValue.getOrThrow(20.seconds)
            assertEquals(0, counter)
            val entities = alice.rpc.startFlow(::GetCustomEntities).returnValue.getOrThrow()
            assertEquals(1, entities.size)
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation caused by a sql statement should save no data`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowFailureAndAssertCreatedEntities(
                flow = ::EntityManagerErrorFromSqlFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 3,
                numberOfExpectedEntities = 0
            )
            alice.rpc.expectFlowFailureAndAssertCreatedEntities(
                flow = ::EntityManagerErrorFromSqlFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 3,
                numberOfExpectedEntities = 1
            )
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation caused by a sql statement that is caught inside an entity manager block saves none of the data inside of it`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            // 1 entity saved from the first entity manager block that does not get rolled back
            // even if there is no intermediate commit to the database
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerCatchErrorFromSqlInsideTheEntityManagerFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerCatchErrorFromSqlInsideTheEntityManagerFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation caused by a sql statement that is caught outside an entity manager block saves none of the data inside of it`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            // 1 entity saved from the first entity manager block that does not get rolled back
            // even if there is no intermediate commit to the database
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerCatchErrorFromSqlOutsideTheEntityManagerFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerCatchErrorFromSqlOutsideTheEntityManagerFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation caused by a sql statement that is caught inside an entity manager and more data is saved afterwards inside the same entity manager should not save the extra data`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerCatchErrorFromSqlAndSaveMoreEntitiesInTheSameEntityManagerFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerCatchErrorFromSqlAndSaveMoreEntitiesInTheSameEntityManagerFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
        }
    }

    @Test(timeout = 300_000)
    fun `constraint violation caused by a sql statement that is caught inside an entity manager and more data is saved afterwards inside a new entity manager should save the extra data`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerCatchErrorFromSqlAndSaveMoreEntitiesInNewEntityManagerFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 2
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerCatchErrorFromSqlAndSaveMoreEntitiesInNewEntityManagerFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 2
            )
        }
    }

    @StartableByRPC
    class EntityManagerSqlFlow : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                createNativeQuery("INSERT INTO $TABLE_NAME VALUES (:id, :name, :quote)")
                    .setParameter("id", anotherEntityWithIdOne.id)
                    .setParameter("name", anotherEntityWithIdOne.name)
                    .setParameter("quote", anotherEntityWithIdOne.name)
                    .executeUpdate()
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerErrorFromSqlFlow(private val commitStatus: CommitStatus) :
        FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                createNativeQuery("INSERT INTO $TABLE_NAME VALUES (:id, :name, :quote)")
                    .setParameter("id", anotherEntityWithIdOne.id)
                    .setParameter("name", anotherEntityWithIdOne.name)
                    .setParameter("quote", anotherEntityWithIdOne.name)
                    .executeUpdate()
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerCatchErrorFromSqlInsideTheEntityManagerFlow(private val commitStatus: CommitStatus) :
        FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                try {
                    createNativeQuery("INSERT INTO $TABLE_NAME VALUES (:id, :name, :quote)")
                        .setParameter("id", anotherEntityWithIdOne.id)
                        .setParameter("name", anotherEntityWithIdOne.name)
                        .setParameter("quote", anotherEntityWithIdOne.name)
                        .executeUpdate()
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerCatchErrorFromSqlOutsideTheEntityManagerFlow(private val commitStatus: CommitStatus) :
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
                    createNativeQuery("INSERT INTO $TABLE_NAME VALUES (:id, :name, :quote)")
                        .setParameter("id", anotherEntityWithIdOne.id)
                        .setParameter("name", anotherEntityWithIdOne.name)
                        .setParameter("quote", anotherEntityWithIdOne.name)
                        .executeUpdate()
                }
            } catch (e: PersistenceException) {
                logger.info("Caught the exception!")
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerCatchErrorFromSqlAndSaveMoreEntitiesInTheSameEntityManagerFlow(private val commitStatus: CommitStatus) :
        FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                try {
                    createNativeQuery("INSERT INTO $TABLE_NAME VALUES (:id, :name, :quote)")
                        .setParameter("id", anotherEntityWithIdOne.id)
                        .setParameter("name", anotherEntityWithIdOne.name)
                        .setParameter("quote", anotherEntityWithIdOne.name)
                        .executeUpdate()
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
                // These entities are not saved since the transaction is marked for rollback
                try {
                    createNativeQuery("INSERT INTO $TABLE_NAME VALUES (:id, :name, :quote)")
                        .setParameter("id", entityWithIdTwo.id)
                        .setParameter("name", entityWithIdTwo.name)
                        .setParameter("quote", entityWithIdTwo.name)
                        .executeUpdate()
                } catch (e: PersistenceException) {
                    if (e.cause is ConstraintViolationException) {
                        throw e
                    } else {
                        logger.info(
                            """
                            Caught exception from second sql statement inside the same broken entity manager
                            This happens if the database has thrown an exception due to rolling back the db transaction
                        """.trimIndent(), e
                        )
                    }
                }
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerCatchErrorFromSqlAndSaveMoreEntitiesInNewEntityManagerFlow(private val commitStatus: CommitStatus) :
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
                    createNativeQuery("INSERT INTO $TABLE_NAME VALUES (:id, :name, :quote)")
                        .setParameter("id", anotherEntityWithIdOne.id)
                        .setParameter("name", anotherEntityWithIdOne.name)
                        .setParameter("quote", anotherEntityWithIdOne.name)
                        .executeUpdate()

                }
            } catch (e: PersistenceException) {
                logger.info("Caught the exception!")
            }
            serviceHub.withEntityManager {
                val query = createNativeQuery("INSERT INTO $TABLE_NAME VALUES (:id, :name, :quote)")
                    .setParameter("id", entityWithIdTwo.id)
                    .setParameter("name", entityWithIdTwo.name)
                    .setParameter("quote", entityWithIdTwo.name)
                query.executeUpdate()
            }
            sleep(1.millis)
        }
    }
}
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
import org.junit.Test
import javax.persistence.PersistenceException
import kotlin.test.assertEquals

class FlowEntityManagerNestedTest : AbstractFlowEntityManagerTest() {

    @Test(timeout = 300_000)
    fun `entity manager inside an entity manager saves all data`() {
        var counter = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ -> ++counter }
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()

            alice.rpc.startFlow(::EntityManagerInsideAnEntityManagerFlow).returnValue.getOrThrow(20.seconds)
            assertEquals(0, counter)
            val entities = alice.rpc.startFlow(::GetCustomEntities).returnValue.getOrThrow()
            assertEquals(2, entities.size)
        }
    }

    @Test(timeout = 300_000)
    fun `entity manager inside an entity manager that throws an error does not save any data`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowFailureAndAssertCreatedEntities(
                flow = ::EntityManagerInsideAnEntityManagerThatThrowsAnExceptionFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 3,
                numberOfExpectedEntities = 0
            )
            alice.rpc.expectFlowFailureAndAssertCreatedEntities(
                flow = ::EntityManagerInsideAnEntityManagerThatThrowsAnExceptionFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 3,
                numberOfExpectedEntities = 1
            )
        }
    }

    @Test(timeout = 300_000)
    fun `entity manager that saves an entity with an entity manager inside it that throws an error after saving the entity does not save any data`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowFailureAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAfterSavingFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 3,
                numberOfExpectedEntities = 0
            )
            alice.rpc.expectFlowFailureAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAfterSavingFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 3,
                numberOfExpectedEntities = 1
            )
        }
    }

    @Test(timeout = 300_000)
    fun `entity manager that saves an entity with an entity manager inside it that throws an error and catching it around the entity manager after saving the entity saves the data from the external entity manager`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesAroundTheEntityManagerAfterSavingFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 2
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesAroundTheEntityManagerAfterSavingFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 2
            )
        }
    }

    @Test(timeout = 300_000)
    fun `entity manager that saves an entity with an entity manager inside it that throws an error and catching it inside the entity manager after saving the entity saves the data from the external entity manager`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesInsideTheEntityManagerAfterSavingFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 2
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesInsideTheEntityManagerAfterSavingFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 2
            )
        }
    }

    @Test(timeout = 300_000)
    fun `entity manager that saves an entity with an entity manager inside it that throws an error and catching it around the entity manager before saving the entity saves the data from the external entity manager`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesAroundTheEntityManagerBeforeSavingFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 2
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesAroundTheEntityManagerBeforeSavingFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 2
            )
        }
    }

    @Test(timeout = 300_000)
    fun `entity manager with an entity manager inside it saves an entity, outer throws and catches the error outside itself after saving the entity does not save the data from the internal entity manager`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityUsingInternalEntityManagerAndThrowsFromOuterAndCatchesAroundOuterEntityManagerAfterSavingFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityUsingInternalEntityManagerAndThrowsFromOuterAndCatchesAroundOuterEntityManagerAfterSavingFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
        }
    }

    @Test(timeout = 300_000)
    fun `entity manager with an entity manager inside it saves an entity, outer throws and catches the error inside itself after saving the entity does not save the data from the internal entity manager`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true)) {

            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityUsingInternalEntityManagerAndThrowsFromOuterAndCatchesInsideOuterEntityManagerAfterSavingFlow,
                commitStatus = CommitStatus.NO_INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
            alice.rpc.expectFlowSuccessAndAssertCreatedEntities(
                flow = ::EntityManagerThatSavesAnEntityUsingInternalEntityManagerAndThrowsFromOuterAndCatchesInsideOuterEntityManagerAfterSavingFlow,
                commitStatus = CommitStatus.INTERMEDIATE_COMMIT,
                numberOfDischarges = 0,
                numberOfExpectedEntities = 1
            )
        }
    }

    @StartableByRPC
    class EntityManagerInsideAnEntityManagerFlow : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
                serviceHub.withEntityManager {
                    persist(entityWithIdTwo)
                }
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerInsideAnEntityManagerThatThrowsAnExceptionFlow(private val commitStatus: CommitStatus) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            serviceHub.withEntityManager {
                persist(entityWithIdOne)
            }
            if (commitStatus == CommitStatus.INTERMEDIATE_COMMIT) {
                sleep(1.millis)
            }
            serviceHub.withEntityManager {
                serviceHub.withEntityManager {
                    persist(anotherEntityWithIdOne)
                }
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAfterSavingFlow(private val commitStatus: CommitStatus) :
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
                persist(entityWithIdTwo)
                serviceHub.withEntityManager {
                    persist(anotherEntityWithIdOne)
                }
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesAroundTheEntityManagerAfterSavingFlow(
        private val commitStatus: CommitStatus
    ) :
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
                persist(entityWithIdTwo)
                try {
                    serviceHub.withEntityManager {
                        persist(anotherEntityWithIdOne)
                    }
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesInsideTheEntityManagerAfterSavingFlow(
        private val commitStatus: CommitStatus
    ) :
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
                persist(entityWithIdTwo)
                serviceHub.withEntityManager {
                    try {
                        persist(anotherEntityWithIdOne)
                        flush()
                    } catch (e: PersistenceException) {
                        logger.info("Caught the exception!")
                    }
                }

            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerThatSavesAnEntityWithAnEntityManagerInsideItThatThrowsAnExceptionAndCatchesAroundTheEntityManagerBeforeSavingFlow(
        private val commitStatus: CommitStatus
    ) :
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
                    serviceHub.withEntityManager {
                        persist(anotherEntityWithIdOne)
                    }
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
                persist(entityWithIdTwo)
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerThatSavesAnEntityUsingInternalEntityManagerAndThrowsFromOuterAndCatchesAroundOuterEntityManagerAfterSavingFlow(
        private val commitStatus: CommitStatus
    ) :
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
                    serviceHub.withEntityManager {
                        persist(entityWithIdTwo)
                    }
                    persist(anotherEntityWithIdOne)
                }
            } catch (e: PersistenceException) {
                logger.info("Caught the exception!")
            }
            sleep(1.millis)
        }
    }

    @StartableByRPC
    class EntityManagerThatSavesAnEntityUsingInternalEntityManagerAndThrowsFromOuterAndCatchesInsideOuterEntityManagerAfterSavingFlow(
        private val commitStatus: CommitStatus
    ) :
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
                serviceHub.withEntityManager {
                    persist(entityWithIdTwo)
                }
                try {
                    persist(anotherEntityWithIdOne)
                    flush()
                } catch (e: PersistenceException) {
                    logger.info("Caught the exception!")
                }
            }
            sleep(1.millis)
        }
    }
}
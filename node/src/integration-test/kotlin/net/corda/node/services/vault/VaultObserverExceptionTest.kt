package net.corda.node.services.vault

import com.r3.dbfailure.workflows.CreateStateFlow
import com.r3.dbfailure.workflows.CreateStateFlow.Initiator
import com.r3.dbfailure.workflows.CreateStateFlow.errorTargetsToNum
import net.corda.core.CordaRuntimeException
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.Permissions
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.User
import net.corda.testing.node.internal.findCordapp
import org.junit.After
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Test
import rx.exceptions.OnErrorNotImplementedException
import java.sql.SQLException
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import javax.persistence.PersistenceException
import kotlin.test.assertFailsWith

class VaultObserverExceptionTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME, DUMMY_NOTARY_NAME)

        val log = contextLogger()

        private fun testCordapps() = listOf(
                findCordapp("com.r3.dbfailure.contracts"),
                findCordapp("com.r3.dbfailure.workflows"),
                findCordapp("com.r3.dbfailure.schemas"))
    }

    @After
    override fun tearDown() {
        super.tearDown()
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.clear()
        StaffedFlowHospital.onFlowKeptForOvernightObservation.clear()
        StaffedFlowHospital.onFlowAdmitted.clear()
    }

    /**
     * Causing an SqlException via a syntax error in a vault observer causes the flow to hit the
     * DatabsaseEndocrinologist in the FlowHospital and being kept for overnight observation
     */
    @Test
    fun unhandledSqlExceptionFromVaultObserverGetsHospitatlised() {
        val testControlFuture = openFuture<Boolean>().toCompletableFuture()

        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.add {
            when (it) {
                is OnErrorNotImplementedException -> Assert.fail("OnErrorNotImplementedException should be unwrapped")
                is SQLException -> {
                    testControlFuture.complete(true)
                }
            }
            false
        }

        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            aliceNode.rpc.startFlow(
                    ::Initiator,
                    "Syntax Error in Custom SQL",
                    CreateStateFlow.errorTargetsToNum(CreateStateFlow.ErrorTarget.ServiceSqlSyntaxError)
            ).returnValue.then { testControlFuture.complete(false) }
            val foundExpectedException = testControlFuture.getOrThrow(30.seconds)

            Assert.assertTrue(foundExpectedException)
        }
    }

    /**
     * Throwing a random (non-SQL releated) exception from a vault observer causes the flow to be
     * aborted when unhandled in user code
     */
    @Test
    fun otherExceptionsFromVaultObserverBringFlowDown() {
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            assertFailsWith(CordaRuntimeException::class, "Toys out of pram") {
                aliceNode.rpc.startFlow(
                        ::Initiator,
                        "InvalidParameterException",
                        CreateStateFlow.errorTargetsToNum(CreateStateFlow.ErrorTarget.ServiceThrowInvalidParameter)
                ).returnValue.getOrThrow(30.seconds)
            }
        }
    }

    /**
     * A random exception from a VaultObserver will bring the Rx Observer down, but can be handled in the flow
     * triggering the observer, and the flow will continue successfully (for some values of success)
     */
    @Test
    fun otherExceptionsFromVaultObserverCanBeSuppressedInFlow() {
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            aliceNode.rpc.startFlow(::Initiator, "InvalidParameterException", CreateStateFlow.errorTargetsToNum(
                    CreateStateFlow.ErrorTarget.ServiceThrowInvalidParameter,
                    CreateStateFlow.ErrorTarget.FlowSwallowErrors))
                    .returnValue.getOrThrow(30.seconds)

        }
    }

    /**
     * If the state we are trying to persist triggers a ConstraintViolation, the flow hospital will retry the flow
     * and keep it in for observation if errors persist.
     */
    @Test
    fun constraintViolationOnCommitGetsRetriedAndThenGetsKeptForObservation() {
        var admitted = 0
        var discharged = 0
        var observation = 0
        StaffedFlowHospital.onFlowAdmitted.add {
            ++admitted
        }
        StaffedFlowHospital.onFlowDischarged.add { _, _ ->
            ++discharged
        }
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observation
        }

        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            assertFailsWith<TimeoutException> {
                aliceNode.rpc.startFlow(::Initiator, "EntityManager", errorTargetsToNum(CreateStateFlow.ErrorTarget.TxInvalidState))
                        .returnValue.getOrThrow(Duration.of(30, ChronoUnit.SECONDS))
            }
        }
        Assert.assertTrue("Exception from service has not been to Hospital", admitted > 0)
        Assert.assertEquals(3, discharged)
        Assert.assertEquals(1, observation)
    }

    /**
     * If we have a state causing a ConstraintViolation lined up for persistence, calling jdbConnection() in
     * the vault observer will trigger a flush that throws. This will be retried, and finally be kept in for observation.
     *
     * 4 discharges due to being handled once by [StaffedFlowHospital.DuplicateInsertSpecialist] and 3 times by
     * [StaffedFlowHospital.TransitionErrorGeneralPractitioner]
     */
    @Test
    fun constraintViolationOnFlushGetsRetriedAndThenGetsKeptForObservation() {
        var counter = 0
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.add {
            when (it) {
                is OnErrorNotImplementedException -> Assert.fail("OnErrorNotImplementedException should be unwrapped")
                is PersistenceException -> {
                    ++counter
                    log.info("Got a PersistentException in the flow hospital count = $counter")
                }
            }
            false
        }
        var discharged = 0
        var observation = 0
        StaffedFlowHospital.onFlowDischarged.add { _, _ ->
            ++discharged
        }
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observation
        }

        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            assertFailsWith<TimeoutException>("ConstraintViolationException") {
                aliceNode.rpc.startFlow(::Initiator, "EntityManager", errorTargetsToNum(
                        CreateStateFlow.ErrorTarget.ServiceValidUpdate,
                        CreateStateFlow.ErrorTarget.TxInvalidState))
                        .returnValue.getOrThrow(30.seconds)
            }
        }
        Assert.assertTrue("Flow has not been to hospital", counter > 0)
        Assert.assertEquals(4, discharged)
        Assert.assertEquals(1, observation)
    }

    /**
     * If we have a state causing a ConstraintViolation lined up for persistence, calling jdbConnection() in
     * the vault observer will trigger a flush that throws. This will be retried, and finally fail.
     * Trying to catch and suppress that exception in the flow around the code triggering the vault observer
     * does not change the outcome - the first exception in the service will bring the service down and will
     * be caught by the flow, but the state machine will error the flow anyway as Corda code threw.
     * On retry, the error will hit the commit, as the observer is dead, and fail as above.
     */
    @Test
    fun constraintViolationOnFlushInVaultObserverCannotBeSuppressedInFlow() {
        var counter = 0
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.add {
            when (it) {
                is OnErrorNotImplementedException -> Assert.fail("OnErrorNotImplementedException should be unwrapped")
                is PersistenceException -> {
                    ++counter
                    log.info("Got a PersistentException in the flow hospital count = $counter")
                }
            }
            false
        }

        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            val flowHandle = aliceNode.rpc.startFlow(
                    ::Initiator,
                    "EntityManager",
                    CreateStateFlow.errorTargetsToNum(
                            CreateStateFlow.ErrorTarget.ServiceValidUpdate,
                            CreateStateFlow.ErrorTarget.TxInvalidState,
                            CreateStateFlow.ErrorTarget.FlowSwallowErrors))
            val flowResult = flowHandle.returnValue
            assertFailsWith<TimeoutException>("ConstraintViolation") { flowResult.getOrThrow(30.seconds) }
            Assert.assertTrue("Flow has not been to hospital", counter > 0)
        }
    }

    /**
     * If we have a state causing a ConstraintViolation lined up for persistence, calling jdbConnection() in
     * the vault observer will trigger a flush that throws. This will be retried, and finally fail.
     * Trying to catch and suppress that exception inside the service does protect the service, but the new
     * interceptor will fail the flow anyway. It will be retried and then be kept in for observation if errors persist.
     */
    @Test
    fun constraintViolationOnFlushInVaultObserverCannotBeSuppressedInService() {
        var counter = 0
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.add {
            when (it) {
                is OnErrorNotImplementedException -> Assert.fail("OnErrorNotImplementedException should be unwrapped")
                is PersistenceException -> {
                    ++counter
                    log.info("Got a PersistentException in the flow hospital count = $counter")
                }
            }
            false
        }

        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            val flowHandle = aliceNode.rpc.startFlow(
                    ::Initiator, "EntityManager",
                    CreateStateFlow.errorTargetsToNum(
                            CreateStateFlow.ErrorTarget.ServiceValidUpdate,
                            CreateStateFlow.ErrorTarget.TxInvalidState,
                            CreateStateFlow.ErrorTarget.ServiceSwallowErrors))
            val flowResult = flowHandle.returnValue
            assertFailsWith<TimeoutException>("ConstraintViolation") { flowResult.getOrThrow(30.seconds) }
            Assert.assertTrue("Flow has not been to hospital", counter > 0)
        }
    }

    /**
     * User code throwing a constraint violation in a raw vault observer will break the recordTransaction call,
     * therefore handling it in flow code is no good, and the error will be passed to the flow hospital via the
     * interceptor.
     */
    @Test
    fun constraintViolationInUserCodeInServiceCannotBeSuppressedInFlow() {
        val testControlFuture = openFuture<Boolean>()
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            log.info("Flow has been kept for overnight observation")
            testControlFuture.set(true)
        }

        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            val flowHandle = aliceNode.rpc.startFlow(::Initiator, "EntityManager", CreateStateFlow.errorTargetsToNum(
                    CreateStateFlow.ErrorTarget.ServiceNullConstraintViolation,
                    CreateStateFlow.ErrorTarget.FlowSwallowErrors))
            val flowResult = flowHandle.returnValue
            flowResult.then {
                log.info("Flow has finished")
                testControlFuture.set(false)
            }
            Assert.assertTrue("Flow has not been kept in hospital", testControlFuture.getOrThrow(30.seconds))
        }
    }

    /**
     * User code throwing a constraint violation and catching suppressing that within the observer code is fine
     * and should not have any impact on the rest of the flow
     */
    @Test
    fun constraintViolationInUserCodeInServiceCanBeSuppressedInService() {
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            val flowHandle = aliceNode.rpc.startFlow(::Initiator, "EntityManager", CreateStateFlow.errorTargetsToNum(
                    CreateStateFlow.ErrorTarget.ServiceNullConstraintViolation,
                    CreateStateFlow.ErrorTarget.ServiceSwallowErrors))
            val flowResult = flowHandle.returnValue
            flowResult.getOrThrow(30.seconds)
        }
    }
}
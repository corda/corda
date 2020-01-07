package net.corda.node.services.vault

import co.paralleluniverse.strands.concurrent.Semaphore
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
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import net.corda.testing.node.internal.findCordapp
import org.junit.After
import org.junit.Assert
import org.junit.Test
import rx.exceptions.OnErrorNotImplementedException
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import javax.persistence.PersistenceException
import kotlin.test.assertFailsWith

class VaultObserverExceptionTest {
    companion object {

        val log = contextLogger()

        private fun testCordapps() = listOf(
                findCordapp("com.r3.dbfailure.contracts"),
                findCordapp("com.r3.dbfailure.workflows"),
                findCordapp("com.r3.dbfailure.schemas"))
    }

    @After
    fun tearDown() {
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.clear()
        StaffedFlowHospital.onFlowKeptForOvernightObservation.clear()
        StaffedFlowHospital.onFlowAdmitted.clear()
    }

    /**
     * Causing an SqlException via a syntax error in a vault observer will be wrapped within a HospitalizeFlowException
     * causes the flow to hit SedationNurse in the FlowHospital and being kept for overnight observation
     */
    // TODO: consolidate this
    @Test
    fun unhandledSqlExceptionFromVaultObserverGetsHospitalised() {
        val testStaffFuture = openFuture<List<String>>().toCompletableFuture()

        StaffedFlowHospital.onFlowKeptForOvernightObservation.add {_, staff ->
            testStaffFuture.complete(staff) // get all staff members that will give an overnight observation diagnosis for this flow
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
            ).returnValue.then { testStaffFuture.complete(listOf()) }
            val staff = testStaffFuture.getOrThrow(30.seconds)

            // flow should have been given an overnight observation diagnosis by the SedationNurse
            Assert.assertTrue(staff.isNotEmpty() && staff.any { it.contains("SedationNurse") })
        }
    }

    /**
     * Throwing a random (non-SQL related) exception from a vault observer causes the flow to be
     * aborted when unhandled in user code
     */
    // TODO: this will now go to hospital; behaviour changed; test needs to change/ consolidate
    // this test used to assert the suppression of exceptions by the triggering flow (other than SQLException and PersistenceException)
    // changed into asserting the same exception getting hospitalised
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
     * None exception thrown from a vault observer can be suppressible in the flow that triggered the observer
     * because the recording of transaction states failed. The flow will be hospitalized.
     * The exception will bring the rx.Observer down.
     */
    @Test
    fun noneExceptionFromVaultObserverCanBeSuppressedInFlow() {
        // this test used to assert the suppression of exceptions by the triggering flow (other than SQLException and PersistenceException)
        // changed into asserting the same exception getting hospitalised
        var observation = 0
        val waitUntilHospitalised = Semaphore(0)
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observation
            waitUntilHospitalised.release()
        }

        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            aliceNode.rpc.startFlow(::Initiator, "InvalidParameterException", CreateStateFlow.errorTargetsToNum(
                    CreateStateFlow.ErrorTarget.ServiceThrowMotherOfAllExceptions,
                    CreateStateFlow.ErrorTarget.FlowSwallowErrors))
            waitUntilHospitalised.acquire() // wait here until flow gets hospitalised
        }

        Assert.assertEquals(1, observation)
    }

    /**
     * If we have a state causing a persistence exception during record transactions (in NodeVaultService#processAndNotify),
     * the flow will be kept in for observation.
     */
    @Test
    fun persistenceExceptionDuringRecordTransactionsGetsKeptForObservation() {
        var counter = 0
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.add {
            when (it) {
                is PersistenceException -> {
                    ++counter
                    log.info("Got a PersistentException in the flow hospital count = $counter")
                }
            }
            false
        }
        var observation = 0
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observation
        }

        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            assertFailsWith<TimeoutException>("PersistenceException") {
                aliceNode.rpc.startFlow(::Initiator, "EntityManager", errorTargetsToNum(
                        CreateStateFlow.ErrorTarget.TxInvalidState))
                        .returnValue.getOrThrow(30.seconds)
            }
        }
        Assert.assertTrue("Flow has not been to hospital", counter > 0)
        Assert.assertEquals(1, observation)
    }

    /**
     * If we have a state causing a persistence exception during record transactions (in NodeVaultService#processAndNotify),
     * trying to catch and suppress that exception inside the flow does protect the flow, but the new
     * interceptor will fail the flow anyway. The flow will be kept in for observation if errors persist.
     */
    @Test
    fun persistenceExceptionDuringRecordTransactionsCannotBeSuppressedInFlow() {
        var counter = 0
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.add {
            when (it) {
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
                            CreateStateFlow.ErrorTarget.TxInvalidState,
                            CreateStateFlow.ErrorTarget.FlowSwallowErrors))
            val flowResult = flowHandle.returnValue
            assertFailsWith<TimeoutException>("PersistenceException") { flowResult.getOrThrow(30.seconds) }
            Assert.assertTrue("Flow has not been to hospital", counter > 0)
        }
    }

    /**
     * User code throwing a syntax error in a raw vault observer will break the recordTransaction call,
     * therefore handling it in flow code is no good, and the error will be passed to the flow hospital via the
     * interceptor.
     */
    @Test
    fun syntaxErrorInUserCodeInServiceCannotBeSuppressedInFlow() {
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
                    CreateStateFlow.ErrorTarget.ServiceSqlSyntaxError,
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
     * User code throwing a syntax error and catching suppressing that within the observer code is fine
     * and should not have any impact on the rest of the flow
     */
    @Test
    fun syntaxErrorInUserCodeInServiceCanBeSuppressedInService() {
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = testCordapps())) {
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            val flowHandle = aliceNode.rpc.startFlow(::Initiator, "EntityManager", CreateStateFlow.errorTargetsToNum(
                    CreateStateFlow.ErrorTarget.ServiceSqlSyntaxError,
                    CreateStateFlow.ErrorTarget.ServiceSwallowErrors))
            val flowResult = flowHandle.returnValue
            flowResult.getOrThrow(30.seconds)
        }
    }
}
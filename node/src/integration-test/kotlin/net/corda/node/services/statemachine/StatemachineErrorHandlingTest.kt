package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.internal.list
import net.corda.core.internal.readAllLines
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.Permissions
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.statemachine.transitions.TopLevelTransition
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalDriverDSL
import org.jboss.byteman.agent.submit.ScriptText
import org.jboss.byteman.agent.submit.Submit
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("MaxLineLength") // Byteman rules cannot be easily wrapped
class StatemachineErrorHandlingTest {

    companion object {
        val rpcUser = User("user1", "test", permissions = setOf(Permissions.all()))
        var counter = 0
    }

    @Before
    fun setup() {
        counter = 0
    }

    /**
     * Throws an exception when performing an [Action.SendInitial] action.
     * The exception is thrown 4 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times) and is then kept in
     * the hospital for observation.
     */
    @Test
    fun `error during transition with SendInitial action is retried 3 times and kept for observation if error persists`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeSendInitial
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeSendInitial action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeSendInitial
                AT ENTRY
                IF readCounter("counter") < 4
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                        30.seconds
                )
            }

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(1, observation)
            assertEquals(1, aliceClient.stateMachinesSnapshot().size)
            // 1 for the errored flow kept for observation and another for GetNumberOfCheckpointsFlow
            assertEquals(2, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.SendInitial] event.
     * The exception is thrown 3 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     */
    @Test
    fun `error during transition with SendInitial action that does not persist will retry and complete successfully`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeSendInitial
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeSendInitial action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeSendInitial
                AT ENTRY
                IF readCounter("counter") < 3
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    30.seconds
            )

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when executing [DeduplicationHandler.afterDatabaseTransaction] from
     * inside an [Action.AcknowledgeMessages] action.
     * The exception is thrown every time [DeduplicationHandler.afterDatabaseTransaction] is executed
     * inside of [ActionExecutorImpl.executeAcknowledgeMessages]
     *
     * The exceptions should be swallowed. Therefore there should be no trips to the hospital and no retries.
     * The flow should complete successfully as the error is swallowed.
     */
    @Test
    fun `error during transition with AcknowledgeMessages action is swallowed and flow completes successfully`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Set flag when inside executeAcknowledgeMessages
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeAcknowledgeMessages
                AT INVOKE ${DeduplicationHandler::class.java.name}.afterDatabaseTransaction()
                IF !flagged("exception_flag")
                DO flag("exception_flag"); traceln("Setting flag to true")
                ENDRULE
                
                RULE Throw exception when executing ${DeduplicationHandler::class.java.name}.afterDatabaseTransaction when inside executeAcknowledgeMessages
                INTERFACE ${DeduplicationHandler::class.java.name}
                METHOD afterDatabaseTransaction
                AT ENTRY
                IF flagged("exception_flag")
                DO traceln("Throwing exception"); clear("exception_flag"); traceln("SETTING FLAG TO FALSE"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    30.seconds
            )

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(0, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(0, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event before the flow has suspended (remains in an unstarted
     * state).
     * The exception is thrown 5 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     *
     * Each time the flow retries, it starts from the beginning of the flow (due to being in an unstarted state).
     *
     * 2 of the thrown exceptions are absorbed by the if statement in [TransitionExecutorImpl.executeTransition] that aborts the transition
     * if an error transition moves into another error transition. The flow still recovers from this state. 5 exceptions were thrown to
     * verify that 3 retries are attempted before recovering.
     */
    @Test
    fun `error during transition with CommitTransaction action that occurs during the beginning of execution will retry and complete successfully`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeCommitTransaction action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF readCounter("counter") < 5
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    30.seconds
            )

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event before the flow has suspended (remains in an unstarted
     * state).
     * The exception is thrown 7 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times) and then be kept in for observation.
     *
     * Each time the flow retries, it starts from the beginning of the flow (due to being in an unstarted state).
     *
     * 2 of the thrown exceptions are absorbed by the if statement in [TransitionExecutorImpl.executeTransition] that aborts the transition
     * if an error transition moves into another error transition. The flow still recovers from this state. 5 exceptions were thrown to
     * verify that 3 retries are attempted before recovering.
     *
     * CORDA-3352 - it is currently hanging after putting the flow in for observation
     */
    @Test
    @Ignore
    fun `error during transition with CommitTransaction action that occurs during the beginning of execution will retry and be kept for observation if error persists`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeCommitTransaction action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF readCounter("counter") < 7
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                        30.seconds
                )
            }

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(1, observation)
            assertEquals(1, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event after the flow has suspended (has moved to a started state).
     * The exception is thrown 5 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     *
     * Each time the flow retries, it begins from the previous checkpoint where it suspended before failing.
     *
     * 2 of the thrown exceptions are absorbed by the if statement in [TransitionExecutorImpl.executeTransition] that aborts the transition
     * if an error transition moves into another error transition. The flow still recovers from this state. 5 exceptions were thrown to
     * verify that 3 retries are attempted before recovering.
     */
    @Test
    fun `error during transition with CommitTransaction action that occurs after the first suspend will retry and complete successfully`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            // seems to be restarting the flow from the beginning every time
            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE
                
                RULE Set flag when executing first suspend
                CLASS ${TopLevelTransition::class.java.name}
                METHOD suspendTransition
                AT ENTRY
                IF !flagged("suspend_flag")
                DO flag("suspend_flag"); traceln("Setting suspend flag to true")
                ENDRULE

                RULE Throw exception on executeCommitTransaction action after first suspend + commit
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && flagged("commit_flag") && readCounter("counter") < 5
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Set flag when executing first commit
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && !flagged("commit_flag")
                DO flag("commit_flag"); traceln("Setting commit flag to true")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    30.seconds
            )

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event when the flow is finishing.
     * The exception is thrown 3 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     *
     * Each time the flow retries, it begins from the previous checkpoint where it suspended before failing.
     */
    @Test
    fun `error during transition with CommitTransaction action that occurs when completing a flow and deleting its checkpoint will retry and complete successfully`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            // seems to be restarting the flow from the beginning every time
            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE
                
                RULE Set flag when adding action to remove checkpoint
                CLASS ${TopLevelTransition::class.java.name}
                METHOD flowFinishTransition
                AT ENTRY
                IF !flagged("remove_checkpoint_flag")
                DO flag("remove_checkpoint_flag"); traceln("Setting remove checkpoint flag to true")
                ENDRULE

                RULE Throw exception on executeCommitTransaction when removing checkpoint
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("remove_checkpoint_flag") && readCounter("counter") < 3
                DO incrementCounter("counter"); clear("remove_checkpoint_flag"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    30.seconds
            )

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when replaying a flow that has already successfully created its initial checkpoint.
     *
     * An exception is thrown when committing a database transaction during a transition to trigger the retry of the flow. Another
     * exception is then thrown during the retry itself.
     *
     * The flow is discharged and replayed from the hospital once. After failing during the replay, the flow is forced into overnight
     * observation. It is not ran again after this point
     */
    @Test
    fun `error during retry of a flow will force the flow into overnight observation`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Set flag when executing first suspend
                CLASS ${TopLevelTransition::class.java.name}
                METHOD suspendTransition
                AT ENTRY
                IF !flagged("suspend_flag")
                DO flag("suspend_flag"); traceln("Setting suspend flag to true")
                ENDRULE
                
                RULE Throw exception on executeCommitTransaction action after first suspend + commit
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && flagged("commit_flag") && !flagged("commit_exception_flag")
                DO flag("commit_exception_flag"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Set flag when executing first commit
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && !flagged("commit_flag")
                DO flag("commit_flag"); traceln("Setting commit flag to true")
                ENDRULE
                
                RULE Throw exception on retry
                CLASS ${SingleThreadedStateMachineManager::class.java.name}
                METHOD addAndStartFlow
                AT ENTRY
                IF flagged("suspend_flag") && flagged("commit_flag") && !flagged("retry_exception_flag")
                DO flag("retry_exception_flag"); traceln("Throwing retry exception"); throw new java.lang.RuntimeException("Here we go again")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                        30.seconds
                )
            }

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(1, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(1, discharge)
            assertEquals(1, observation)
            assertEquals(1, aliceClient.stateMachinesSnapshot().size)
            // 1 for the errored flow kept for observation and another for GetNumberOfCheckpointsFlow
            assertEquals(2, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when replaying a flow that has already successfully created its initial checkpoint.
     *
     * An exception is thrown when committing a database transaction during a transition to trigger the retry of the flow. Another
     * exception is then thrown during the database commit that comes as part of retrying a flow.
     *
     * The flow is discharged and replayed from the hospital once. When the database commit failure occurs as part of retrying the
     * flow, the starting and completion of the retried flow is affected. In other words, the error occurs as part of the replay, but the
     * flow will still finish successfully. This is due to the even being scheduled as part of the retry and the failure in the database
     * commit occurs after this point. As the flow is already scheduled, the failure has not affect on it.
     */
    @Test
    fun `error during commit transaction action when retrying a flow will retry the flow again and complete successfully`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Set flag when executing first suspend
                CLASS ${TopLevelTransition::class.java.name}
                METHOD suspendTransition
                AT ENTRY
                IF !flagged("suspend_flag")
                DO flag("suspend_flag"); traceln("Setting suspend flag to true")
                ENDRULE
                
                RULE Throw exception on executeCommitTransaction action after first suspend + commit
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && flagged("commit_flag") && !flagged("commit_exception_flag")
                DO flag("commit_exception_flag"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Set flag when executing first commit
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && !flagged("commit_flag")
                DO flag("commit_flag"); traceln("Setting commit flag to true")
                ENDRULE
                
                RULE Throw exception on retry
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && flagged("commit_exception_flag") && !flagged("retry_exception_flag")
                DO flag("retry_exception_flag"); traceln("Throwing retry exception"); throw new java.lang.RuntimeException("Here we go again")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    30.seconds
            )

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(1, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(1, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when replaying a flow that has not made its initial checkpoint.
     *
     * An exception is thrown when committing a database transaction during a transition to trigger the retry of the flow. Another
     * exception is then thrown during the retry itself.
     *
     * The flow is discharged and replayed from the hospital once. After failing during the replay, the flow is forced into overnight
     * observation. It is not ran again after this point
     *
     * CORDA-3352 - it is currently hanging after putting the flow in for observation
     *
     */
    @Test
    @Ignore
    fun `error during retrying a flow that failed when committing its original checkpoint will force the flow into overnight observation`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Throw exception on executeCommitTransaction action after first suspend + commit
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF !flagged("commit_exception_flag")
                DO flag("commit_exception_flag"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Throw exception on retry
                CLASS ${SingleThreadedStateMachineManager::class.java.name}
                METHOD onExternalStartFlow
                AT ENTRY
                IF flagged("commit_exception_flag") && !flagged("retry_exception_flag")
                DO flag("retry_exception_flag"); traceln("Throwing retry exception"); throw new java.lang.RuntimeException("Here we go again")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                        30.seconds
                )
            }

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(1, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(1, discharge)
            assertEquals(1, observation)
            assertEquals(1, aliceClient.stateMachinesSnapshot().size)
            // 1 for the errored flow kept for observation and another for GetNumberOfCheckpointsFlow
            assertEquals(2, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws a [ConstraintViolationException] when performing an [Action.CommitTransaction] event when the flow is finishing.
     * The exception is thrown 4 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times) and then be kept in for observation.
     *
     * Each time the flow retries, it begins from the previous checkpoint where it suspended before failing.
     */
    @Test
    fun `error during transition with CommitTransaction action and ConstraintViolationException that occurs when completing a flow will retry and be kept for observation if error persists`() {
        startDriver {
            val charlie = createNode(CHARLIE_NAME)
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE
                
                RULE Set flag when adding action to remove checkpoint
                CLASS ${TopLevelTransition::class.java.name}
                METHOD flowFinishTransition
                AT ENTRY
                IF !flagged("remove_checkpoint_flag")
                DO flag("remove_checkpoint_flag"); traceln("Setting remove checkpoint flag to true")
                ENDRULE

                RULE Throw exception on executeCommitTransaction when removing checkpoint
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("remove_checkpoint_flag") && readCounter("counter") < 4
                DO incrementCounter("counter"); 
                    clear("remove_checkpoint_flag"); 
                    traceln("Throwing exception"); 
                    throw new org.hibernate.exception.ConstraintViolationException("This flow has a terminal condition", new java.sql.SQLException(), "made up constraint")
                ENDRULE
                
                RULE Entering duplicate insert staff member
                CLASS ${StaffedFlowHospital.DuplicateInsertSpecialist::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached duplicate insert staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.DuplicateInsertSpecialist::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment not my speciality counter
                CLASS ${StaffedFlowHospital.DuplicateInsertSpecialist::class.java.name}
                METHOD consult
                AT READ NOT_MY_SPECIALTY
                IF true
                DO traceln("Byteman test - not my speciality")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                        30.seconds
                )
            }

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - not my speciality") }.size)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(1, observation)
            assertEquals(1, aliceClient.stateMachinesSnapshot().size)
            // 1 for errored flow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event on a responding flow. The failure prevents the node from saving
     * its original checkpoint.
     *
     * The exception is thrown 5 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     *
     * Each time the flow retries, it starts from the beginning of the flow (due to being in an unstarted state).
     *
     * 2 of the thrown exceptions are absorbed by the if statement in [TransitionExecutorImpl.executeTransition] that aborts the transition
     * if an error transition moves into another error transition. The flow still recovers from this state. 5 exceptions were thrown to verify
     * that 3 retries are attempted before recovering.
     */
    @Test
    fun `responding flow - error during transition with CommitTransaction action that occurs during the beginning of execution will retry and complete successfully`() {
        startDriver {
            val charlie = createBytemanNode(CHARLIE_NAME)
            val alice = createNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeCommitTransaction action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF readCounter("counter") < 5
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            val charlieClient =
                    CordaRPCClient(charlie.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    30.seconds
            )

            val output = getBytemanOutput(charlie)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = charlieClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            assertEquals(0, charlieClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, charlieClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event on a responding flow. The failure prevents the node from saving
     * its original checkpoint.
     *
     * The exception is thrown 5 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times) and then be kept in for observation.
     *
     * Each time the flow retries, it starts from the beginning of the flow (due to being in an unstarted state).
     *
     * 2 of the thrown exceptions are absorbed by the if statement in [TransitionExecutorImpl.executeTransition] that aborts the transition
     * if an error transition moves into another error transition. The flow still recovers from this state. 5 exceptions were thrown to verify
     * that 3 retries are attempted before recovering.
     *
     * The final asserts for checking the checkpoints on the nodes are correct since the responding node can replay the flow starting events
     * from artemis. Therefore, the checkpoint is missing due the failures from saving the original checkpoint. But, the node will still be
     * able to recover when the node is restarted (by using the events). The initiating flow maintains the checkpoint as it is waiting for
     * the responding flow to recover and finish its flow.
     */
    @Test
    fun `responding flow - error during transition with CommitTransaction action that occurs during the beginning of execution will retry and be kept for observation if error persists`() {
        startDriver {
            val charlie = createBytemanNode(CHARLIE_NAME)
            val alice = createNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeCommitTransaction action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF readCounter("counter") < 7
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            val charlieClient =
                    CordaRPCClient(charlie.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                        30.seconds
                )
            }

            val output = getBytemanOutput(charlie)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = charlieClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(1, observation)
            assertEquals(1, aliceClient.stateMachinesSnapshot().size)
            assertEquals(1, charlieClient.stateMachinesSnapshot().size)
            // 1 for the flow that is waiting for the errored counterparty flow to finish and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for GetNumberOfCheckpointsFlow
            // the checkpoint is not persisted since it kept failing the original checkpoint commit
            // the flow will recover since artemis will keep the events and replay them on node restart
            assertEquals(1, charlieClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event when the flow is finishing on a responding node.
     *
     * The exception is thrown 3 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     */
    @Test
    fun `responding flow - error during transition with CommitTransaction action that occurs when completing a flow and deleting its checkpoint will retry and complete successfully`() {
        startDriver {
            val charlie = createBytemanNode(CHARLIE_NAME)
            val alice = createNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE
                
                RULE Set flag when adding action to remove checkpoint
                CLASS ${TopLevelTransition::class.java.name}
                METHOD flowFinishTransition
                AT ENTRY
                IF !flagged("remove_checkpoint_flag")
                DO flag("remove_checkpoint_flag"); traceln("Setting remove checkpoint flag to true")
                ENDRULE

                RULE Throw exception on executeCommitTransaction when removing checkpoint
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("remove_checkpoint_flag") && readCounter("counter") < 3
                DO incrementCounter("counter"); 
                    clear("remove_checkpoint_flag"); 
                    traceln("Throwing exception"); 
                    throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            val charlieClient =
                    CordaRPCClient(charlie.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    30.seconds
            )

            val output = getBytemanOutput(charlie)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = charlieClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            assertEquals(0, charlieClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, charlieClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when recoding a transaction inside of [ReceiveFinalityFlow] on the responding
     * flow's node.
     *
     * The flow is kept in for observation.
     *
     * Only the responding node keeps a checkpoint. The initiating flow has completed successfully as it has complete its
     * send to the responding node and the responding node successfully received it.
     */
    @Test
    fun `error recording a transaction inside of ReceiveFinalityFlow will keep the flow in for observation` () {
        startDriver(notarySpec = NotarySpec(DUMMY_NOTARY_NAME, validating = false)) {
            val charlie = createBytemanNode(CHARLIE_NAME, FINANCE_CORDAPPS)
            val alice = createNode(ALICE_NAME, FINANCE_CORDAPPS)

            // could not get rule for FinalityDoctor + observation counter to work
            val rules = """
                RULE Set flag when entering receive finality flow
                CLASS ${ReceiveFinalityFlow::class.java.name}
                METHOD call
                AT ENTRY
                IF !flagged("finality_flag")
                DO flag("finality_flag"); traceln("Setting finality flag")
                ENDRULE
                
                RULE Set flag when leaving resolve transactions flow
                CLASS ${ResolveTransactionsFlow::class.java.name}
                METHOD call
                AT EXIT
                IF !flagged("resolve_tx_flag")
                DO flag("resolve_tx_flag"); traceln("Setting resolve tx flag")
                ENDRULE

                RULE Throw exception when recording transaction
                INTERFACE ${ServiceHubInternal::class.java.name}
                METHOD recordTransactions
                AT ENTRY
                IF flagged("finality_flag") && flagged("resolve_tx_flag")
                DO traceln("Throwing exception"); 
                    throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            val charlieClient =
                    CordaRPCClient(charlie.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(
                    ::CashIssueAndPaymentFlow,
                    500.DOLLARS,
                    OpaqueBytes.of(0x01),
                    charlie.nodeInfo.singleIdentity(),
                    false,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow(30.seconds)

            val (discharge, observation) = charlieClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(0, discharge)
            assertEquals(1, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            assertEquals(1, charlieClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 ReceiveFinalityFlow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, charlieClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when resolving a transaction's dependencies inside of [ReceiveFinalityFlow] on the responding
     * flow's node.
     *
     * The flow is kept in for observation.
     *
     * Only the responding node keeps a checkpoint. The initiating flow has completed successfully as it has complete its
     * send to the responding node and the responding node successfully received it.
     */
    @Test
    fun `error resolving a transaction's dependencies inside of ReceiveFinalityFlow will keep the flow in for observation`() {
        startDriver(notarySpec = NotarySpec(DUMMY_NOTARY_NAME, validating = false)) {
            val charlie = createBytemanNode(CHARLIE_NAME, FINANCE_CORDAPPS)
            val alice = createNode(ALICE_NAME, FINANCE_CORDAPPS)

            // could not get rule for FinalityDoctor + observation counter to work
            val rules = """
                RULE Set flag when entering receive finality flow
                CLASS ${ReceiveFinalityFlow::class.java.name}
                METHOD call
                AT ENTRY
                IF !flagged("finality_flag")
                DO flag("finality_flag"); traceln("Setting finality flag")
                ENDRULE
                
                RULE Set flag when entering resolve transactions flow
                CLASS ${ResolveTransactionsFlow::class.java.name}
                METHOD call
                AT ENTRY
                IF !flagged("resolve_tx_flag")
                DO flag("resolve_tx_flag"); traceln("Setting resolve tx flag")
                ENDRULE

                RULE Throw exception when recording transaction
                INTERFACE ${ServiceHubInternal::class.java.name}
                METHOD recordTransactions
                AT ENTRY
                IF flagged("finality_flag") && flagged("resolve_tx_flag")
                DO traceln("Throwing exception"); 
                    throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            val charlieClient =
                    CordaRPCClient(charlie.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(
                    ::CashIssueAndPaymentFlow,
                    500.DOLLARS,
                    OpaqueBytes.of(0x01),
                    charlie.nodeInfo.singleIdentity(),
                    false,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow(30.seconds)

            val (discharge, observation) = charlieClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(0, discharge)
            assertEquals(1, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            assertEquals(1, charlieClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for ReceiveFinalityFlow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, charlieClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when executing [Action.CommitTransaction] as part of receiving a transaction to record inside of [ReceiveFinalityFlow] on the responding
     * flow's node.
     *
     * The exception is thrown 5 times.
     *
     * The responding flow is retried 3 times and then completes successfully.
     *
     * The [StaffedFlowHospital.TransitionErrorGeneralPractitioner] catches these errors instead of the [StaffedFlowHospital.FinalityDoctor]. Due to this, the
     * flow is retried instead of moving straight to observation.
     */
    @Test
    fun `error during transition with CommitTransaction action while receiving a transaction inside of ReceiveFinalityFlow will be retried and complete successfully`() {
        startDriver(notarySpec = NotarySpec(DUMMY_NOTARY_NAME, validating = false)) {
            val charlie = createBytemanNode(CHARLIE_NAME, FINANCE_CORDAPPS)
            val alice = createNode(ALICE_NAME, FINANCE_CORDAPPS)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE
                
                RULE Set flag when entering receive finality flow
                CLASS ${ReceiveFinalityFlow::class.java.name}
                METHOD call
                AT ENTRY
                IF !flagged("finality_flag")
                DO flag("finality_flag"); traceln("Setting finality flag")
                ENDRULE
                
                RULE Throw exception on executeCommitTransaction action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("finality_flag") && readCounter("counter") < 5
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            val charlieClient =
                    CordaRPCClient(charlie.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(
                    ::CashIssueAndPaymentFlow,
                    500.DOLLARS,
                    OpaqueBytes.of(0x01),
                    charlie.nodeInfo.singleIdentity(),
                    false,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow(30.seconds)

            val output = getBytemanOutput(charlie)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = charlieClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            assertEquals(0, charlieClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, charlieClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when executing [Action.CommitTransaction] as part of receiving a transaction to record inside of [ReceiveFinalityFlow] on the responding
     * flow's node.
     *
     * The exception is thrown 7 times.
     *
     * The responding flow is retried 3 times and is then kept in for observation.
     *
     * Both the initiating node and the responding node keep checkpoints for their flows. The initiating node keeps a checkpoint for the original flow that is
     * waiting for the responding flow's receive to complete. The responding flow's checkpoint is kept due to it failing the commit as part of receive.
     *
     * The [StaffedFlowHospital.TransitionErrorGeneralPractitioner] catches these errors instead of the [StaffedFlowHospital.FinalityDoctor]. Due to this, the
     * flow is retried instead of moving straight to observation.
     */
    @Test
    fun `error during transition with CommitTransaction action while receiving a transaction inside of ReceiveFinalityFlow will be retried and be kept for observation is error persists`() {
        startDriver(notarySpec = NotarySpec(DUMMY_NOTARY_NAME, validating = false)) {
            val charlie = createBytemanNode(CHARLIE_NAME, FINANCE_CORDAPPS)
            val alice = createNode(ALICE_NAME, FINANCE_CORDAPPS)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE
                
                RULE Set flag when entering receive finality flow
                CLASS ${ReceiveFinalityFlow::class.java.name}
                METHOD call
                AT ENTRY
                IF !flagged("finality_flag")
                DO flag("finality_flag"); traceln("Setting finality flag")
                ENDRULE
                
                RULE Throw exception on executeCommitTransaction action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("finality_flag") && readCounter("counter") < 7
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy
            val charlieClient =
                    CordaRPCClient(charlie.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(
                        ::CashIssueAndPaymentFlow,
                        500.DOLLARS,
                        OpaqueBytes.of(0x01),
                        charlie.nodeInfo.singleIdentity(),
                        false,
                        defaultNotaryIdentity
                ).returnValue.getOrThrow(30.seconds)
            }

            val output = getBytemanOutput(charlie)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val (discharge, observation) = charlieClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(1, observation)
            assertEquals(1, aliceClient.stateMachinesSnapshot().size)
            assertEquals(1, charlieClient.stateMachinesSnapshot().size)
            // 1 for CashIssueAndPaymentFlow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for ReceiveFinalityFlow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, charlieClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Triggers `killFlow` while the flow is suspended causing a [InterruptedException] to be thrown and passed through the hospital.
     *
     * The flow terminates and is not retried.
     *
     * No pass through the hospital is recorded. As the flow is marked as `isRemoved`.
     */
    @Test
    fun `error during transition due to an InterruptedException (killFlow) will terminate the flow`() {
        startDriver {
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
                
                RULE Increment terminal counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ TERMINAL
                IF true
                DO traceln("Byteman test - terminal")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            val flow = aliceClient.startTrackedFlow(::SleepFlow)

            var flowKilled = false
            flow.progress.subscribe {
                if (it == SleepFlow.STARTED.label) {
                    Thread.sleep(5000)
                    flowKilled = aliceClient.killFlow(flow.id)
                }
            }

            assertFailsWith<TimeoutException> { flow.returnValue.getOrThrow(20.seconds) }

            val output = getBytemanOutput(alice)

            assertTrue(flowKilled)
            // Check the stdout for the lines generated by byteman
            assertEquals(0, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val numberOfTerminalDiagnoses = output.filter { it.contains("Byteman test - terminal") }.size
            assertEquals(1, numberOfTerminalDiagnoses)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(0, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Triggers `killFlow` during user application code.
     *
     * The user application code is mimicked by a [Thread.sleep] which is importantly not placed inside the [Suspendable]
     * call function. Placing it inside a [Suspendable] function causes quasar to behave unexpectedly.
     *
     * Although the call to kill the flow is made during user application code. It will not be removed / stop processing
     * until the next suspension point is reached within the flow.
     *
     * The flow terminates and is not retried.
     *
     * No pass through the hospital is recorded. As the flow is marked as `isRemoved`.
     */
    @Test
    fun `flow killed during user code execution stops and removes the flow correctly`() {
        startDriver {
            val alice = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
                
                RULE Increment terminal counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ TERMINAL
                IF true
                DO traceln("Byteman test - terminal")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            val flow = aliceClient.startTrackedFlow(::ThreadSleepFlow)

            var flowKilled = false
            flow.progress.subscribe {
                if (it == ThreadSleepFlow.STARTED.label) {
                    Thread.sleep(5000)
                    flowKilled = aliceClient.killFlow(flow.id)
                }
            }

            assertFailsWith<TimeoutException> { flow.returnValue.getOrThrow(30.seconds) }

            val output = getBytemanOutput(alice)

            assertTrue(flowKilled)
            // Check the stdout for the lines generated by byteman
            assertEquals(0, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val numberOfTerminalDiagnoses = output.filter { it.contains("Byteman test - terminal") }.size
            println(numberOfTerminalDiagnoses)
            assertEquals(0, numberOfTerminalDiagnoses)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(0, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Triggers `killFlow` after the flow has already been sent to observation. The flow is not running at this point and
     * all that remains is its checkpoint in the database.
     *
     * The flow terminates and is not retried.
     *
     * Killing the flow does not lead to any passes through the hospital. All the recorded passes through the hospital are
     * from the original flow that was put in for observation.
     */
    @Test
    fun `flow killed when it is in the flow hospital for observation is removed correctly`() {
        startDriver {
            val alice = createBytemanNode(ALICE_NAME)
            val charlie = createNode(CHARLIE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeSendInitial
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeSendInitial action
                CLASS ${ActionExecutorImpl::class.java.name}
                METHOD executeSendInitial
                AT ENTRY
                IF readCounter("counter") < 4
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Entering internal error staff member
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT ENTRY
                IF true
                DO traceln("Reached internal transition error staff member")
                ENDRULE

                RULE Increment discharge counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ DISCHARGE
                IF true
                DO traceln("Byteman test - discharging")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
                
                RULE Increment terminal counter
                CLASS ${StaffedFlowHospital.TransitionErrorGeneralPractitioner::class.java.name}
                METHOD consult
                AT READ TERMINAL
                IF true
                DO traceln("Byteman test - terminal")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules)

            val aliceClient =
                    CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            val flow = aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity())

            assertFailsWith<TimeoutException> { flow.returnValue.getOrThrow(20.seconds) }

            aliceClient.killFlow(flow.id)

            val output = getBytemanOutput(alice)

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - overnight observation") }.size)
            val numberOfTerminalDiagnoses = output.filter { it.contains("Byteman test - terminal") }.size
            assertEquals(0, numberOfTerminalDiagnoses)
            val (discharge, observation) = aliceClient.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(1, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    private fun startDriver(notarySpec: NotarySpec = NotarySpec(DUMMY_NOTARY_NAME), dsl: DriverDSL.() -> Unit) {
        driver(
                DriverParameters(
                        notarySpecs = listOf(notarySpec),
                        startNodesInProcess = false,
                        inMemoryDB = false,
                        systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
                )
        ) {
            dsl()
        }
    }

    private fun DriverDSL.createBytemanNode(
            providedName: CordaX500Name,
            additionalCordapps: Collection<TestCordapp> = emptyList()
    ): NodeHandle {
        return (this as InternalDriverDSL).startNode(
                NodeParameters(
                        providedName = providedName,
                        rpcUsers = listOf(rpcUser),
                        additionalCordapps = additionalCordapps
                ),
                bytemanPort = 12000
        ).getOrThrow()
    }

    private fun DriverDSL.createNode(providedName: CordaX500Name, additionalCordapps: Collection<TestCordapp> = emptyList()): NodeHandle {
        return startNode(
                NodeParameters(
                        providedName = providedName,
                        rpcUsers = listOf(rpcUser),
                        additionalCordapps = additionalCordapps
                )
        ).getOrThrow()
    }

    private fun submitBytemanRules(rules: String) {
        val submit = Submit("localhost", 12000)
        submit.addScripts(listOf(ScriptText("Test script", rules)))
    }

    private fun getBytemanOutput(nodeHandle: NodeHandle): List<String> {
        return nodeHandle.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()
    }
}

@StartableByRPC
@InitiatingFlow
class SendAMessageFlow(private val party: Party) : FlowLogic<String>() {
    @Suspendable
    override fun call(): String {
        val session = initiateFlow(party)
        session.send("hello there")
        return "Finished executing test flow - ${this.runId}"
    }
}

@InitiatedBy(SendAMessageFlow::class)
class SendAMessageResponder(private val session: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
    }
}

@StartableByRPC
class SleepFlow : FlowLogic<Unit>() {

    object STARTED : ProgressTracker.Step("I am ready to die")

    override val progressTracker = ProgressTracker(STARTED)

    @Suspendable
    override fun call() {
        sleep(Duration.of(1, ChronoUnit.SECONDS))
        progressTracker.currentStep = STARTED
        sleep(Duration.of(2, ChronoUnit.MINUTES))
    }
}

@StartableByRPC
class ThreadSleepFlow : FlowLogic<Unit>() {

    object STARTED : ProgressTracker.Step("I am ready to die")

    override val progressTracker = ProgressTracker(STARTED)

    @Suspendable
    override fun call() {
        sleep(Duration.of(1, ChronoUnit.SECONDS))
        progressTracker.currentStep = STARTED
        logger.info("Starting ${ThreadSleepFlow::class.qualifiedName} application sleep")
        sleep()
        logger.info("Finished ${ThreadSleepFlow::class.qualifiedName} application sleep")
        sleep(Duration.of(2, ChronoUnit.MINUTES))
    }

    // Sleep is moved outside of `@Suspendable` function to prevent issues with Quasar
    private fun sleep() {
        Thread.sleep(20000)
    }
}

@StartableByRPC
class GetNumberOfCheckpointsFlow : FlowLogic<Long>() {
    override fun call(): Long {
        return serviceHub.jdbcSession().prepareStatement("select count(*) from node_checkpoints").use { ps ->
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }
}

@StartableByRPC
class GetHospitalCountersFlow : FlowLogic<HospitalCounts>() {
    override fun call(): HospitalCounts = HospitalCounts(
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
        StaffedFlowHospital.onFlowDischarged.add { _, _ ->
            ++dischargeCounter
        }
        StaffedFlowHospital.onFlowKeptForOvernightObservation.add { _, _ ->
            ++observationCounter
        }
    }
}
package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.statemachine.transitions.StartedFlowTransition
import net.corda.node.services.statemachine.transitions.TopLevelTransition
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("MaxLineLength") // Byteman rules cannot be easily wrapped
class StateMachineGeneralErrorHandlingTest : StateMachineErrorHandlingTest() {

    private companion object {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
    }

    /**
     * Throws an exception when performing an [Action.SendInitial] action.
     *
     * The exception is thrown 4 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times) and is then kept in
     * the hospital for observation.
     */
    @Test(timeout = 300_000)
    fun `error during transition with SendInitial action is retried 3 times and kept for observation if error persists`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS $actionExecutorClassName
                METHOD executeSendMultiple
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeSendMultiple action
                CLASS $actionExecutorClassName
                METHOD executeSendMultiple
                AT ENTRY
                IF readCounter("counter") < 4
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    StateMachineErrorHandlingTest::SendAMessageFlow,
                    charlie.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(
                    30.seconds
                )
            }

            alice.rpc.assertNumberOfCheckpoints(hospitalized = 1)
            alice.rpc.assertHospitalCounts(
                discharged = 3,
                observation = 1
            )
            assertEquals(1, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when performing an [Action.SendInitial] event.
     *
     * The exception is thrown 3 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     */
    @Test(timeout = 300_000)
    fun `error during transition with SendInitial action that does not persist will retry and complete successfully`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS $actionExecutorClassName
                METHOD executeSendMultiple
                AT ENTRY
                IF createCounter("counter", $counter)
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeSendMultiple action
                CLASS $actionExecutorClassName
                METHOD executeSendMultiple
                AT ENTRY
                IF readCounter("counter") < 3
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            alice.rpc.startFlow(
                StateMachineErrorHandlingTest::SendAMessageFlow,
                charlie.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(
                30.seconds
            )

            alice.rpc.assertNumberOfCheckpointsAllZero()
            alice.rpc.assertHospitalCounts(discharged = 3)
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when executing [DeduplicationHandler.afterDatabaseTransaction] from inside an [Action.AcknowledgeMessages] action.
     *
     * The exception is thrown every time [DeduplicationHandler.afterDatabaseTransaction] is executed inside of
     * [ActionExecutorImpl.executeAcknowledgeMessages]
     *
     * The exceptions should be swallowed. Therefore there should be no trips to the hospital and no retries.
     * The flow should complete successfully as the error is swallowed.
     */
    @Test(timeout = 300_000)
    fun `error during transition with AcknowledgeMessages action is swallowed and flow completes successfully`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            val rules = """
                RULE Set flag when inside executeAcknowledgeMessages
                CLASS $actionExecutorClassName
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
            """.trimIndent()

            submitBytemanRules(rules, port)

            alice.rpc.startFlow(
                StateMachineErrorHandlingTest::SendAMessageFlow,
                charlie.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(
                30.seconds
            )

            alice.rpc.assertNumberOfCheckpointsAllZero()
            alice.rpc.assertHospitalCountsAllZero()
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event when trying to propagate an error (processing an
     * [Event.StartErrorPropagation] event)
     *
     * The exception is thrown 3 times.
     *
     * This causes the flow to retry the [Event.StartErrorPropagation] event until it succeeds. This this scenario it is retried 3 times,
     * on the final retry the flow successfully propagates the error and completes exceptionally.
     */
    @Test(timeout = 300_000)
    fun `error during error propagation the flow is able to retry and recover`() {
        startDriver {
            val (alice, port) = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS ${ThrowAnErrorFlow::class.java.name}
                METHOD throwException
                AT ENTRY
                IF !flagged("my_flag")
                DO traceln("SETTING FLAG TO TRUE"); flag("my_flag")
                ENDRULE

                RULE Throw exception on executeCommitTransaction action
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("my_flag") && readCounter("counter") < 3
                DO traceln("Throwing exception"); incrementCounter("counter"); throw new java.sql.SQLException("die dammit die", "1")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            assertFailsWith<CordaRuntimeException> {
                alice.rpc.startFlow(StateMachineErrorHandlingTest::ThrowAnErrorFlow).returnValue.getOrThrow(60.seconds)
            }

            alice.rpc.assertNumberOfCheckpointsAllZero()
            alice.rpc.assertHospitalCounts(
                propagated = 1,
                propagatedRetry = 3
            )
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when replaying a flow that has already successfully created its initial checkpoint.
     *
     * An exception is thrown when committing a database transaction during a transition to trigger the retry of the flow. Another
     * exception is then thrown during the retry itself.
     *
     * The flow is discharged and replayed from the hospital. An exception is then thrown during the retry that causes the flow to be
     * retried again.
     */
    @Test(timeout = 300_000)
    fun `error during flow retry when executing retryFlowFromSafePoint the flow is able to retry and recover`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            val rules = """
                RULE Set flag when executing first suspend
                CLASS ${TopLevelTransition::class.java.name}
                METHOD suspendTransition
                AT ENTRY
                IF !flagged("suspend_flag")
                DO flag("suspend_flag"); traceln("Setting suspend flag to true")
                ENDRULE
                
                RULE Throw exception on executeCommitTransaction action after first suspend + commit
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && flagged("commit_flag") && !flagged("commit_exception_flag")
                DO flag("commit_exception_flag"); traceln("Throwing exception"); throw new java.sql.SQLException("die dammit die", "1")
                ENDRULE
                
                RULE Set flag when executing first commit
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && !flagged("commit_flag")
                DO flag("commit_flag"); traceln("Setting commit flag to true")
                ENDRULE
                
                RULE Throw exception on retry
                CLASS $stateMachineManagerClassName
                METHOD addAndStartFlow
                AT ENTRY
                IF flagged("suspend_flag") && flagged("commit_flag") && !flagged("retry_exception_flag")
                DO flag("retry_exception_flag"); traceln("Throwing retry exception"); throw new java.lang.RuntimeException("Here we go again")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            alice.rpc.startFlow(
                StateMachineErrorHandlingTest::SendAMessageFlow,
                charlie.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(40.seconds)

            alice.rpc.assertNumberOfCheckpointsAllZero()
            alice.rpc.assertHospitalCounts(
                discharged = 1,
                dischargedRetry = 1
            )
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event after the flow has suspended (has moved to a started state).
     *
     * The exception is thrown 3 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     *
     * Each time the flow retries, it begins from the previous checkpoint where it suspended before failing.
     */
    @Test(timeout = 300_000)
    fun `error during transition with CommitTransaction action that occurs after the first suspend will retry and complete successfully`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            // seems to be restarting the flow from the beginning every time
            val rules = """
                RULE Create Counter
                CLASS $actionExecutorClassName
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
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && flagged("commit_flag") && readCounter("counter") < 3
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Set flag when executing first commit
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("suspend_flag") && !flagged("commit_flag")
                DO flag("commit_flag"); traceln("Setting commit flag to true")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            alice.rpc.startFlow(
                StateMachineErrorHandlingTest::SendAMessageFlow,
                charlie.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(
                30.seconds
            )

            alice.rpc.assertNumberOfCheckpointsAllZero()
            alice.rpc.assertHospitalCounts(discharged = 3)
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event when the flow is finishing.
     *
     * The exception is thrown 3 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     *
     * Each time the flow retries, it begins from the previous checkpoint where it suspended before failing.
     */
    @Test(timeout = 300_000)
    fun `error during transition with CommitTransaction action that occurs when completing a flow and deleting its checkpoint will retry and complete successfully`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            // seems to be restarting the flow from the beginning every time
            val rules = """
                RULE Create Counter
                CLASS $actionExecutorClassName
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
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("remove_checkpoint_flag") && readCounter("counter") < 3
                DO incrementCounter("counter"); clear("remove_checkpoint_flag"); traceln("Throwing exception"); throw new java.sql.SQLException("die dammit die", "1")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            alice.rpc.startFlow(
                StateMachineErrorHandlingTest::SendAMessageFlow,
                charlie.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(
                30.seconds
            )

            alice.rpc.assertNumberOfCheckpointsAllZero()
            alice.rpc.assertHospitalCounts(discharged = 3)
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws a [ConstraintViolationException] when performing an [Action.CommitTransaction] event when the flow is finishing.
     *
     * The exception is thrown 4 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times) and then be kept in for observation.
     *
     * Each time the flow retries, it begins from the previous checkpoint where it suspended before failing.
     */
    @Test(timeout = 300_000)
    fun `error during transition with CommitTransaction action and ConstraintViolationException that occurs when completing a flow will retry and be kept for observation if error persists`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS $actionExecutorClassName
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
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("remove_checkpoint_flag") && readCounter("counter") < 4
                DO incrementCounter("counter"); 
                    clear("remove_checkpoint_flag"); 
                    traceln("Throwing exception"); 
                    throw new org.hibernate.exception.ConstraintViolationException("This flow has a terminal condition", new java.sql.SQLException(), "made up constraint")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(
                    StateMachineErrorHandlingTest::SendAMessageFlow,
                    charlie.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow(
                    30.seconds
                )
            }

            alice.rpc.assertNumberOfCheckpoints(hospitalized = 1)
            alice.rpc.assertHospitalCounts(
                discharged = 3,
                observation = 1
            )
            assertEquals(1, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event before the flow has suspended (remains in an unstarted
     * state).
     *
     * The exception is thrown 3 times.
     *
     * An exception is also thrown from [CheckpointStorage.getCheckpoint].
     *
     * This test is to prevent a regression, where a transient database connection error can be thrown retrieving a flow's checkpoint when
     * retrying the flow after it failed to commit it's original checkpoint.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * succeeds and the flow finishes.
     */
    @Test(timeout = 300_000)
    fun `flow can be retried when there is a transient connection error to the database`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter) && createCounter("counter_2", $counter) 
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeCommitTransaction action
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF readCounter("counter") < 3
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Throw exception on getCheckpoint
                INTERFACE ${CheckpointStorage::class.java.name}
                METHOD getCheckpoint
                AT ENTRY
                IF readCounter("counter_2") < 3
                DO incrementCounter("counter_2"); traceln("Throwing exception getting checkpoint"); throw new java.sql.SQLTransientConnectionException("Connection is not available")
                ENDRULE
                
                RULE Log external start flow event
                CLASS $stateMachineManagerClassName
                METHOD onExternalStartFlow
                AT ENTRY
                IF true
                DO traceln("External start flow event")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            alice.rpc.startFlow(
                StateMachineErrorHandlingTest::SendAMessageFlow,
                charlie.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(
                30.seconds
            )

            alice.rpc.assertNumberOfCheckpointsAllZero()
            alice.rpc.assertHospitalCounts(
                discharged = 3,
                dischargedRetry = 1
            )
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event before the flow has suspended (remains in an unstarted
     * state).
     *
     * The exception is thrown 4 times.
     *
     * An exception is also thrown from [CheckpointStorage.getCheckpoint].
     *
     * This test is to prevent a regression, where a transient database connection error can be thrown retrieving a flow's checkpoint when
     * retrying the flow after it failed to commit it's original checkpoint.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the transition
     * fails and is kept for in for observation.
     */
    @Test(timeout = 300_000)
    fun `flow can be retried when there is a transient connection error to the database goes to observation if error persists`() {
        startDriver {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME)

            val rules = """
                RULE Create Counter
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF createCounter("counter", $counter) && createCounter("counter_2", $counter) 
                DO traceln("Counter created")
                ENDRULE

                RULE Throw exception on executeCommitTransaction action
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF readCounter("counter") < 4
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Throw exception on getCheckpoint
                INTERFACE ${CheckpointStorage::class.java.name}
                METHOD getCheckpoint
                AT ENTRY
                IF readCounter("counter_2") < 3
                DO incrementCounter("counter_2"); traceln("Throwing exception getting checkpoint"); throw new java.sql.SQLTransientConnectionException("Connection is not available")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            executor.execute {
                alice.rpc.startFlow(StateMachineErrorHandlingTest::SendAMessageFlow, charlie.nodeInfo.singleIdentity())
            }

            // flow is not signaled as started calls to [getOrThrow] will hang, sleeping instead
            Thread.sleep(30.seconds.toMillis())

            alice.rpc.assertNumberOfCheckpoints(hospitalized = 1)
            alice.rpc.assertHospitalCounts(
                discharged = 3,
                observation = 1,
                dischargedRetry = 1
            )
            assertEquals(1, alice.rpc.stateMachinesSnapshot().size)
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
    @Test(timeout = 300_000)
    fun `responding flow - error during transition with CommitTransaction action that occurs when completing a flow and deleting its checkpoint will retry and complete successfully`() {
        startDriver {
            val (alice, charlie, port) = createNodeAndBytemanNode(ALICE_NAME, CHARLIE_NAME)

            val rules = """
                RULE Create Counter
                CLASS $actionExecutorClassName
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
                CLASS $actionExecutorClassName
                METHOD executeCommitTransaction
                AT ENTRY
                IF flagged("remove_checkpoint_flag") && readCounter("counter") < 3
                DO incrementCounter("counter"); 
                    clear("remove_checkpoint_flag"); 
                    traceln("Throwing exception"); 
                    throw new java.sql.SQLException("die dammit die", "1")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            alice.rpc.startFlow(
                StateMachineErrorHandlingTest::SendAMessageFlow,
                charlie.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow(
                30.seconds
            )

            alice.rpc.assertNumberOfCheckpointsAllZero()
            charlie.rpc.assertNumberOfCheckpointsAllZero()
            charlie.rpc.assertHospitalCounts(discharged = 3)
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
            assertEquals(0, charlie.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when creating a transition.
     *
     * The exception is thrown back to the flow, who catches it and returns a different exception, showing the exception returns to user
     * code and can be caught if needed.
     */
    @Test(timeout = 300_000)
    fun `error during creation of transition that occurs after the first suspend will throw error into flow`() {
        startDriver {
            val (alice, port) = createBytemanNode(ALICE_NAME)

            val rules = """
                RULE Throw exception when creating transition
                CLASS ${StartedFlowTransition::class.java.name}
                METHOD sleepTransition
                AT ENTRY
                IF true
                DO traceln("Throwing exception"); throw new java.lang.IllegalStateException("die dammit die")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            assertThatExceptionOfType(FlowException::class.java).isThrownBy {
                alice.rpc.startFlow(::SleepCatchAndRethrowFlow).returnValue.getOrThrow(30.seconds)
            }.withMessage("java.lang.IllegalStateException: die dammit die")

            alice.rpc.assertNumberOfCheckpointsAllZero()
            alice.rpc.assertHospitalCounts(propagated = 1)
            assertEquals(0, alice.rpc.stateMachinesSnapshot().size)
        }
    }

    /**
     * Throws an exception when calling [FlowStateMachineImpl.logFlowError] to cause an unexpected error after the flow has properly
     * initialised, placing the flow into a dead state.
     *
     * On shutdown this flow will still terminate correctly and not prevent the node from shutting down.
     */
    @Test(timeout = 300_000)
    fun `a dead flow can be shutdown`() {
        startDriver {
            val (alice, port) = createBytemanNode(ALICE_NAME)
            val rules = """
                RULE Throw exception
                CLASS ${FlowStateMachineImpl::class.java.name}
                METHOD logFlowError
                AT ENTRY
                IF readCounter("counter") < 1
                DO incrementCounter("counter"); traceln("Throwing exception"); throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
                
                RULE Log that state machine has ended
                CLASS $stateMachineManagerClassName
                METHOD stop
                AT EXIT
                IF true
                DO traceln("State machine shutdown")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            assertFailsWith<TimeoutException> {
                alice.rpc.startFlow(::ThrowAnErrorFlow).returnValue.getOrThrow(50.seconds)
            }

            val (discharge, observation) = alice.rpc.startFlow(::GetHospitalCountersFlow).returnValue.get()
            assertEquals(0, discharge)
            assertEquals(1, observation)
            assertEquals(1, alice.rpc.stateMachinesSnapshot().size)
            alice.rpc.assertNumberOfCheckpoints(hospitalized = 1)

            try {
                // This actually shuts down the node
                alice.rpc.shutdown()
            } catch(e: Exception) {
                // Exception gets thrown due to shutdown
            }
            Thread.sleep(30.seconds.toMillis())
            alice.assertBytemanOutput("State machine shutdown", 1)
        }
    }

    @StartableByRPC
    class SleepCatchAndRethrowFlow : FlowLogic<String>() {
        @Suspendable
        override fun call(): String {
            try {
                sleep(5.seconds)
            } catch (e: IllegalStateException) {
                throw FlowException(e)
            }
            return "cant get here"
        }
    }
}

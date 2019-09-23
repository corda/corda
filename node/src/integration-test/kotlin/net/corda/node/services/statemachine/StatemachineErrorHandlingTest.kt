package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.list
import net.corda.core.internal.readAllLines
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.statemachine.transitions.TopLevelTransition
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.User
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

class StatemachineErrorHandlingTest : IntegrationTest() {

    companion object {
        val databaseSchemas = IntegrationTestSchemas(CHARLIE_NAME, ALICE_NAME, DUMMY_NOTARY_NAME)
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
        driver(
            DriverParameters(
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            val charlie = startNode(
                NodeParameters(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(rpcUser)
                )
            ).getOrThrow()
            val alice =
                (this as InternalDriverDSL).startNode(
                    NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)),
                    bytemanPort = 12000
                ).getOrThrow()

            val submit = Submit("localhost", 12000)

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

            submit.addScripts(listOf(ScriptText("Test script", rules)))

            val aliceClient =
                CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    Duration.of(30, ChronoUnit.SECONDS)
                )
            }

            val output = alice.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - overnight observation") }.size)
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
        driver(
            DriverParameters(
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            val charlie = startNode(
                NodeParameters(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(rpcUser)
                )
            ).getOrThrow()
            val alice =
                (this as InternalDriverDSL).startNode(
                    NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)),
                    bytemanPort = 12000
                ).getOrThrow()

            val submit = Submit("localhost", 12000)

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

            submit.addScripts(listOf(ScriptText("Test script", rules)))

            val aliceClient =
                CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                Duration.of(30, ChronoUnit.SECONDS)
            )

            val output = alice.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when executing [DeduplicationHandler.afterDatabaseTransaction] from inside an [Action.AcknowledgeMessages] action.
     * The exception is thrown every time [DeduplicationHandler.afterDatabaseTransaction] is executed inside of [ActionExecutorImpl.executeAcknowledgeMessages]
     *
     * The exceptions should be swallowed. Therefore there should be no trips to the hospital and no retries.
     * The flow should complete successfully as the error is swallowed.
     */
    @Test
    fun `error during transition with AcknowledgeMessages action is swallowed and flow completes successfully`() {
        driver(
            DriverParameters(
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            val charlie = startNode(
                NodeParameters(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(rpcUser)
                )
            ).getOrThrow()
            val alice =
                (this as InternalDriverDSL).startNode(
                    NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)),
                    bytemanPort = 12000
                ).getOrThrow()

            val submit = Submit("localhost", 12000)

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

            submit.addScripts(listOf(ScriptText("Test script", rules)))

            val aliceClient =
                CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                Duration.of(30, ChronoUnit.SECONDS)
            )

            val output = alice.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()

            // Check the stdout for the lines generated by byteman
            assertEquals(0, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event before the flow has suspended (remains in an unstarted state)..
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
    fun `error during transition with CommitTransaction action that occurs during the beginning of execution will retry and complete successfully`() {
        driver(
            DriverParameters(
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            val charlie = startNode(
                NodeParameters(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(rpcUser)
                )
            ).getOrThrow()
            val alice =
                (this as InternalDriverDSL).startNode(
                    NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)),
                    bytemanPort = 12000
                ).getOrThrow()

            val submit = Submit("localhost", 12000)

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

            submit.addScripts(listOf(ScriptText("Test script", rules)))

            val aliceClient =
                CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                Duration.of(30, ChronoUnit.SECONDS)
            )

            val output = alice.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws an exception when performing an [Action.CommitTransaction] event before the flow has suspended (remains in an unstarted state)..
     * The exception is thrown 7 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times) and then be kept in for observation.
     *
     * Each time the flow retries, it starts from the beginning of the flow (due to being in an unstarted state).
     *
     * 2 of the thrown exceptions are absorbed by the if statement in [TransitionExecutorImpl.executeTransition] that aborts the transition
     * if an error transition moves into another error transition. The flow still recovers from this state. 5 exceptions were thrown to verify
     * that 3 retries are attempted before recovering.
     *
     * TODO Fix this scenario - it is currently hanging after putting the flow in for observation
     */
    @Test
    @Ignore
    fun `error during transition with CommitTransaction action that occurs during the beginning of execution will retry and be kept for observation if error persists`() {
        driver(
            DriverParameters(
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            val charlie = startNode(
                NodeParameters(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(rpcUser)
                )
            ).getOrThrow()
            val alice =
                (this as InternalDriverDSL).startNode(
                    NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)),
                    bytemanPort = 12000
                ).getOrThrow()

            val submit = Submit("localhost", 12000)

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

            submit.addScripts(listOf(ScriptText("Test script", rules)))

            val aliceClient =
                CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<TimeoutException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    Duration.of(30, ChronoUnit.SECONDS)
                )
            }

            val output = alice.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - overnight observation") }.size)
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
     * if an error transition moves into another error transition. The flow still recovers from this state. 5 exceptions were thrown to verify
     * that 3 retries are attempted before recovering.
     */
    @Test
    fun `error during transition with CommitTransaction action that occurs after the first suspend will retry and complete successfully`() {
        driver(
            DriverParameters(
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            val charlie = startNode(
                NodeParameters(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(rpcUser)
                )
            ).getOrThrow()
            val alice =
                (this as InternalDriverDSL).startNode(
                    NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)),
                    bytemanPort = 12000
                ).getOrThrow()

            val submit = Submit("localhost", 12000)

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

            submit.addScripts(listOf(ScriptText("Test script", rules)))

            val aliceClient =
                CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                Duration.of(30, ChronoUnit.SECONDS)
            )

            val output = alice.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
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
        driver(
            DriverParameters(
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            val charlie = startNode(
                NodeParameters(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(rpcUser)
                )
            ).getOrThrow()
            val alice =
                (this as InternalDriverDSL).startNode(
                    NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)),
                    bytemanPort = 12000
                ).getOrThrow()

            val submit = Submit("localhost", 12000)

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

            submit.addScripts(listOf(ScriptText("Test script", rules)))

            val aliceClient =
                CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                Duration.of(30, ChronoUnit.SECONDS)
            )

            val output = alice.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()

            // Check the stdout for the lines generated by byteman
            assertEquals(3, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }

    /**
     * Throws a [ConstraintViolationException] when performing an [Action.CommitTransaction] event when the flow is finishing.
     * The exception is thrown 4 times.
     *
     * This causes the transition to be discharged from the hospital 3 times (retries 3 times). On the final retry the error
     * propagates and the flow ends.
     *
     * Each time the flow retries, it begins from the previous checkpoint where it suspended before failing.
     */
    @Test
    fun `error during transition with CommitTransaction action and ConstraintViolationException that occurs when completing a flow will retry and fail if error persists`() {
        driver(
            DriverParameters(
                startNodesInProcess = false,
                inMemoryDB = false,
                systemProperties = mapOf("co.paralleluniverse.fibers.verifyInstrumentation" to "true")
            )
        ) {
            val charlie = startNode(
                NodeParameters(
                    providedName = CHARLIE_NAME,
                    rpcUsers = listOf(rpcUser)
                )
            ).getOrThrow()
            val alice =
                (this as InternalDriverDSL).startNode(
                    NodeParameters(providedName = ALICE_NAME, rpcUsers = listOf(rpcUser)),
                    bytemanPort = 12000
                ).getOrThrow()

            val submit = Submit("localhost", 12000)

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
                IF flagged("remove_checkpoint_flag") && readCounter("counter") < 5
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
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.DuplicateInsertSpecialist::class.java.name}
                METHOD consult
                AT READ OVERNIGHT_OBSERVATION
                IF true
                DO traceln("Byteman test - overnight observation")
                ENDRULE
                
                RULE Increment observation counter
                CLASS ${StaffedFlowHospital.DuplicateInsertSpecialist::class.java.name}
                METHOD consult
                AT READ TERMINAL
                IF true
                DO traceln("Byteman test - terminal")
                ENDRULE
            """.trimIndent()

            submit.addScripts(listOf(ScriptText("Test script", rules)))

            val aliceClient =
                CordaRPCClient(alice.rpcAddress).start(rpcUser.username, rpcUser.password).proxy

            assertFailsWith<StateTransitionException> {
                aliceClient.startFlow(::SendAMessageFlow, charlie.nodeInfo.singleIdentity()).returnValue.getOrThrow(
                    Duration.of(30, ChronoUnit.SECONDS)
                )
            }

            val output = alice.baseDirectory
                .list()
                .first { it.toString().contains("net.corda.node.Corda") && it.toString().contains("stdout.log") }
                .readAllLines()

            // Check the stdout for the lines generated by byteman
            assertEquals(4, output.filter { it.contains("Byteman test - discharging") }.size)
            assertEquals(0, output.filter { it.contains("Byteman test - overnight observation") }.size)
            assertEquals(1, output.filter { it.contains("Byteman test - terminal") }.size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(::GetNumberOfCheckpointsFlow).returnValue.get())
        }
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
package net.corda.node.services.statemachine

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.node.services.api.ServiceHubInternal
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.Test
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("MaxLineLength") // Byteman rules cannot be easily wrapped
class StatemachineFinalityErrorHandlingTest : StatemachineErrorHandlingTest() {

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
    fun `error recording a transaction inside of ReceiveFinalityFlow will keep the flow in for observation`() {
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

            val (discharge, observation) = charlieClient.startFlow(StatemachineErrorHandlingTest::GetHospitalCountersFlow).returnValue.get()
            assertEquals(0, discharge)
            assertEquals(1, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            assertEquals(1, charlieClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(StatemachineErrorHandlingTest::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 ReceiveFinalityFlow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, charlieClient.startFlow(StatemachineErrorHandlingTest::GetNumberOfCheckpointsFlow).returnValue.get())
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

            val (discharge, observation) = charlieClient.startFlow(StatemachineErrorHandlingTest::GetHospitalCountersFlow).returnValue.get()
            assertEquals(0, discharge)
            assertEquals(1, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            assertEquals(1, charlieClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(StatemachineErrorHandlingTest::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for ReceiveFinalityFlow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, charlieClient.startFlow(StatemachineErrorHandlingTest::GetNumberOfCheckpointsFlow).returnValue.get())
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
            val (discharge, observation) = charlieClient.startFlow(StatemachineErrorHandlingTest::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(0, observation)
            assertEquals(0, aliceClient.stateMachinesSnapshot().size)
            assertEquals(0, charlieClient.stateMachinesSnapshot().size)
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, aliceClient.startFlow(StatemachineErrorHandlingTest::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for GetNumberOfCheckpointsFlow
            assertEquals(1, charlieClient.startFlow(StatemachineErrorHandlingTest::GetNumberOfCheckpointsFlow).returnValue.get())
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
            val (discharge, observation) = charlieClient.startFlow(StatemachineErrorHandlingTest::GetHospitalCountersFlow).returnValue.get()
            assertEquals(3, discharge)
            assertEquals(1, observation)
            assertEquals(1, aliceClient.stateMachinesSnapshot().size)
            assertEquals(1, charlieClient.stateMachinesSnapshot().size)
            // 1 for CashIssueAndPaymentFlow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, aliceClient.startFlow(StatemachineErrorHandlingTest::GetNumberOfCheckpointsFlow).returnValue.get())
            // 1 for ReceiveFinalityFlow and 1 for GetNumberOfCheckpointsFlow
            assertEquals(2, charlieClient.startFlow(StatemachineErrorHandlingTest::GetNumberOfCheckpointsFlow).returnValue.get())
        }
    }
}
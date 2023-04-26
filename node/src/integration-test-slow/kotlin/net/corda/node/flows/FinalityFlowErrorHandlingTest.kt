package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaRuntimeException
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.test.flows.CashIssueWithObserversFlow
import net.corda.node.services.statemachine.StateMachineErrorHandlingTest
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.flows.waitForAllFlowsToComplete
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class FinalityFlowErrorHandlingTest : StateMachineErrorHandlingTest() {

    /**
     * Throws an exception after recording an issuance transaction but before broadcasting the transaction to observer sessions.
     *
     */
    @Test(timeout = 300_000)
    fun `error after recording an issuance transaction inside of FinalityFlow generates recovery metadata`() {
        startDriver(notarySpec = NotarySpec(DUMMY_NOTARY_NAME, validating = false),
                    extraCordappPackagesToScan = listOf("net.corda.node.flows")) {
            val (charlie, alice, port) = createNodeAndBytemanNode(CHARLIE_NAME, ALICE_NAME, FINANCE_CORDAPPS + enclosedCordapp())

            val rules = """
                RULE Set flag when entering receive finality flow
                CLASS ${FinalityFlow::class.java.name}
                METHOD call
                AT ENTRY
                IF !flagged("finality_flag")
                DO flag("finality_flag"); traceln("Setting finality flag")
                ENDRULE

                RULE Throw exception when recording transaction
                CLASS ${FinalityFlow::class.java.name}
                METHOD finaliseLocallyAndBroadcast
                AT EXIT
                IF flagged("finality_flag")
                DO traceln("Throwing exception");
                    throw new java.lang.RuntimeException("die dammit die")
                ENDRULE
            """.trimIndent()

            submitBytemanRules(rules, port)

            try {
                alice.rpc.startFlow(
                        ::CashIssueWithObserversFlow,
                        500.DOLLARS,
                        OpaqueBytes.of(0x01),
                        defaultNotaryIdentity,
                        setOf(charlie.nodeInfo.singleIdentity())
                ).returnValue.getOrThrow(30.seconds)
                fail()
            }
            catch (e: CordaRuntimeException) {
                waitForAllFlowsToComplete(alice)
                val txId = alice.rpc.stateMachineRecordedTransactionMappingSnapshot().single().transactionId

                alice.rpc.startFlow(::GetFlowTransaction, txId).returnValue.getOrThrow().apply {
                    assertEquals("V", this.first)              // "V" -> VERIFIED
                    assertEquals(ALICE_NAME.toString(), this.second)    // initiator
                    assertEquals(CHARLIE_NAME.toString(), this.third)   // peers
                }
            }
        }
    }
}

// Internal use for testing only!!
@StartableByRPC
class GetFlowTransaction(private val txId: SecureHash) : FlowLogic<Triple<String, String, String>>() {
    @Suspendable
    override fun call(): Triple<String, String, String> {
        return serviceHub.jdbcSession().prepareStatement("select * from node_transactions where tx_id = ?")
                .apply { setString(1, txId.toString()) }
                .use { ps ->
                    ps.executeQuery().use { rs ->
                        rs.next()
                        Triple(rs.getString(4),   // TransactionStatus
                               rs.getString(7),   // initiator
                               rs.getString(8))   // participants
                    }
                }
    }
}
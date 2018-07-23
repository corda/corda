package net.corda.instrumentation.byteman

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.*
import net.corda.testing.driver.*
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.internal.toDatabaseSchemaNames
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.DummyClusterSpec
import net.corda.testing.node.internal.InternalDriverDSL
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jboss.byteman.agent.submit.ScriptText
import org.jboss.byteman.agent.submit.Submit
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InstrumentationTest : IntegrationTest() {
    private lateinit var alice: NodeHandle
    private lateinit var notaryNodes: List<OutOfProcess>
    private lateinit var aliceProxy: CordaRPCOps
    private lateinit var raftNotaryIdentity: Party
    private var bytemanPort: Int = -1

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*DUMMY_NOTARY_NAME.toDatabaseSchemaNames("_0", "_1", "_2").toTypedArray(),
                ALICE_NAME.toDatabaseSchemaName())

        private val logger = contextLogger()

        private val testUser = User("test", "test", permissions = setOf(
                startFlow<CashIssueFlow>(),
                startFlow<CashPaymentFlow>(),
                invokeRpc(CordaRPCOps::nodeInfo),
                invokeRpc(CordaRPCOps::stateMachineRecordedTransactionMappingSnapshot))
        )

        private fun connectRpc(node: NodeHandle): CordaRPCOps {
            val client = CordaRPCClient(node.rpcAddress)
            return client.start(testUser.username, testUser.password).proxy
        }
    }
    private fun setup(inMemoryDB: Boolean = true, testBlock: InternalDriverDSL.() -> Unit) {

        val portAllocation = PortAllocation.Incremental(10000)

        internalDriver(
                cordappsForAllNodes = cordappsForPackages("net.corda.finance.contracts", "net.corda.finance.schemas"),
                portAllocation = portAllocation,
                notarySpecs = listOf(
                        NotarySpec(
                                DUMMY_NOTARY_NAME,
                                rpcUsers = listOf(testUser),
                                cluster = DummyClusterSpec(clusterSize = 1))
                ),
                inMemoryDB = inMemoryDB
        ) {

            bytemanPort = portAllocation.nextPort()
            alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(testUser), bytemanPort = bytemanPort).getOrThrow()
            raftNotaryIdentity = defaultNotaryIdentity
            notaryNodes = defaultNotaryHandle.nodeHandles.getOrThrow().map { it as OutOfProcess }

            assertThat(notaryNodes).hasSize(1)

            aliceProxy = connectRpc(alice)

            testBlock()
        }
    }

    @Test
    fun testRulesInstall() {
        setup {

            val submit = Submit("localhost", bytemanPort)
            logger.info("Byteman agent version used: " + submit.agentVersion)
            logger.info("Remote system properties: " + submit.listSystemProperties())

            val countDownReached = "Countdown reached"
            val deploymentOutcome = submit.addScripts(listOf(ScriptText("My test script", """
RULE CashIssue invocation logging
CLASS net.corda.finance.flows.CashIssueFlow
METHOD call
AT ENTRY
IF TRUE
DO System.out.println("Installing paymentCounter countdown")
ENDRULE

RULE Create CountDown
CLASS net.corda.finance.flows.CashIssueFlow
METHOD call
AT EXIT
IF TRUE
DO createCountDown("paymentCounter", 10)
ENDRULE

RULE trace CashPaymentFlow.call
CLASS net.corda.finance.flows.CashPaymentFlow
METHOD call
AT ENTRY
IF TRUE
DO debug("CashPaymentFlow invoked")
ENDRULE

RULE Decrement CountDown and throw
CLASS net.corda.finance.flows.CashPaymentFlow
METHOD call
AT EXIT
IF countDown("paymentCounter")
DO throw new java.lang.IllegalStateException("$countDownReached")
ENDRULE
""")))
            assertThat(deploymentOutcome).contains("install rule Decrement CountDown and throw")
            assertThat(submit.listAllRules()).contains(countDownReached)

            // Issue 100 pounds, then pay ourselves 10x5 pounds
            issueCash(100.POUNDS)

            // Submit 10 successful payments
            for (i in 1..10) {
                paySelf(5.POUNDS)
            }

            // 11th payment should fail as countDown has been reached
            assertThatThrownBy { paySelf(5.POUNDS) }.hasMessageContaining(countDownReached)
        }
    }

    private fun issueCash(amount: Amount<Currency>) {
        aliceProxy.startFlow(::CashIssueFlow, amount, OpaqueBytes.of(0), raftNotaryIdentity).returnValue.getOrThrow()
    }

    private fun paySelf(amount: Amount<Currency>) = aliceProxy.startFlow(::CashPaymentFlow, amount, alice.nodeInfo.singleIdentity()).returnValue.getOrThrow()

    @Test
    fun testNodeRestart() {
        setup(inMemoryDB = false) {

            val submit = Submit("localhost", bytemanPort)

            val deploymentOutcome = submit.addScripts(listOf(ScriptText("My restart script", """
RULE Create CountDown
CLASS net.corda.finance.flows.CashIssueFlow
METHOD call
AT EXIT
IF TRUE
DO createCountDown("paymentCounter", 10)
ENDRULE

RULE Decrement CountDown and kill
CLASS net.corda.finance.flows.CashPaymentFlow
METHOD call
AT INVOKE net.corda.core.node.ServiceHub.signInitialTransaction
IF countDown("paymentCounter")
DO debug("Killing JVM now!"); killJVM()
ENDRULE
""")))
            assertThat(deploymentOutcome).contains("install rule Decrement CountDown and kill")
            assertThat(submit.listAllRules()).contains("killJVM")

            // Issue 100 pounds, then pay ourselves 10x5 pounds
            issueCash(100.POUNDS)

            // Submit 10 successful payments
            val successfulPayments = (1..10).map { paySelf(5.POUNDS) }

            // 11th payment should be done against killed JVM
            assertThatThrownBy { paySelf(5.POUNDS) }.isInstanceOf(RPCException::class.java).hasMessageContaining("Connection failure detected")

            // Alice node should no longer be responsive or alive
            assertFalse((alice as OutOfProcess).process.isAlive)
            assertThatThrownBy { aliceProxy.nodeInfo() }.isInstanceOf(RPCException::class.java).hasMessageContaining("RPC server is not available")

            // Restart node
            alice.stop() // this should perform un-registration in the NetworkMap
            alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(testUser), bytemanPort = bytemanPort).getOrThrow()
            aliceProxy = connectRpc(alice)

            // Check that all 11 transactions are present
            val snapshot = aliceProxy.stateMachineRecordedTransactionMappingSnapshot()
            assertEquals(11, snapshot.size)
            Assertions.assertThat(snapshot.map { it.transactionId }.toSet()).containsAll(successfulPayments.map { it.stx.id })

            // Make an extra payment to ensure that node is operational
            val anotherPaymentOutcome = paySelf(5.POUNDS)
            assertEquals(500, (anotherPaymentOutcome.stx.tx.outputStates.first() as Cash.State).amount.quantity)
        }
    }
}
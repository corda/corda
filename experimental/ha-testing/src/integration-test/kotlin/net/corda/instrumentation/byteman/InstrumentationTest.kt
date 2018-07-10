package net.corda.instrumentation.byteman

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
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
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jboss.byteman.agent.submit.ScriptText
import org.jboss.byteman.agent.submit.Submit
import org.junit.ClassRule
import org.junit.Test
import java.util.*

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

        val logger = contextLogger()
    }
    private fun setup(compositeIdentity: Boolean = false, testBlock: () -> Unit) {
        val testUser = User("test", "test", permissions = setOf(
                startFlow<CashIssueFlow>(),
                startFlow<CashPaymentFlow>(),
                invokeRpc(CordaRPCOps::nodeInfo),
                invokeRpc(CordaRPCOps::stateMachinesFeed))
        )
        val portAllocation = PortAllocation.Incremental(10000)

        internalDriver(
                extraCordappPackagesToScan = listOf("net.corda.finance.contracts", "net.corda.finance.schemas"),
                portAllocation = portAllocation,
                notarySpecs = listOf(
                        NotarySpec(
                                DUMMY_NOTARY_NAME,
                                rpcUsers = listOf(testUser),
                                cluster = DummyClusterSpec(clusterSize = 1, compositeServiceIdentity = compositeIdentity))
                )
        ) {

            bytemanPort = portAllocation.nextPort()
            alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(testUser), bytemanPort = bytemanPort).getOrThrow()
            raftNotaryIdentity = defaultNotaryIdentity
            notaryNodes = defaultNotaryHandle.nodeHandles.getOrThrow().map { it as OutOfProcess }

            assertThat(notaryNodes).hasSize(1)

            for (notaryNode in notaryNodes) {
                assertThat(notaryNode.nodeInfo.legalIdentities).contains(raftNotaryIdentity)
            }

            // Check that each notary has different identity as a node.
            assertThat(notaryNodes.flatMap { it.nodeInfo.legalIdentities - raftNotaryIdentity }.toSet()).hasSameSizeAs(notaryNodes)

            // Connect to Alice and the notaries
            fun connectRpc(node: NodeHandle): CordaRPCOps {
                val client = CordaRPCClient(node.rpcAddress)
                return client.start("test", "test").proxy
            }
            aliceProxy = connectRpc(alice)

            testBlock()
        }
    }

    @Test
    fun test() {
        setup {

            val submit = Submit("localhost", bytemanPort)
            logger.info("Byteman agent version used: " + submit.agentVersion)
            logger.info("Remote system properties: " + submit.listSystemProperties())

            val COUNTDOWN_REACHED_STR = "Countdown reached"
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
DO throw new java.lang.IllegalStateException("$COUNTDOWN_REACHED_STR")
ENDRULE
""")))
            assertThat(deploymentOutcome).contains("install rule Decrement CountDown and throw")
            assertThat(submit.listAllRules()).contains(COUNTDOWN_REACHED_STR)

            // Issue 100 pounds, then pay ourselves 10x5 pounds
            issueCash(100.POUNDS)

            // Submit 10 successful payments
            for (i in 1..10) {
                paySelf(5.POUNDS)
            }

            // 11th payment should fail as countDown has been reached
            assertThatThrownBy { paySelf(5.POUNDS) }.hasMessageContaining(COUNTDOWN_REACHED_STR)
        }
    }

    private fun issueCash(amount: Amount<Currency>) {
        aliceProxy.startFlow(::CashIssueFlow, amount, OpaqueBytes.of(0), raftNotaryIdentity).returnValue.getOrThrow()
    }

    private fun paySelf(amount: Amount<Currency>) {
        aliceProxy.startFlow(::CashPaymentFlow, amount, alice.nodeInfo.singleIdentity()).returnValue.getOrThrow()
    }
}
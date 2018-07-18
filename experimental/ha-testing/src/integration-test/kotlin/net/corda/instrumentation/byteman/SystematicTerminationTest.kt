package net.corda.instrumentation.byteman

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.contracts.Amount
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.*
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.OnLedgerAsset
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.Permissions
import net.corda.node.services.messaging.RPCServer
import net.corda.node.services.statemachine.ActionExecutorImpl
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.node.User
import net.corda.testing.node.internal.InternalDriverDSL
import net.corda.testing.node.internal.internalDriver
import net.corda.testing.node.internal.poll
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl
import org.assertj.core.api.Assertions
import org.jboss.byteman.agent.submit.ScriptText
import org.jboss.byteman.agent.submit.Submit
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@RunWith(Parameterized::class)
class SystematicTerminationTest(private val terminationData: TerminationData) : IntegrationTest() {

    private lateinit var alice: NodeHandle
    private lateinit var aliceProxy: CordaRPCOps
    private lateinit var raftNotaryIdentity: Party
    private var bytemanPort: Int = -1

    data class TerminationData(val terminationTarget: Method, val counterValue: Int)

    private val pollExecutor = Executors.newScheduledThreadPool(1)

    companion object {

        private val logger = contextLogger()

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {

            return listOf(
                    // We try to locate a single method where termination is meant to be performed.
                    // This of course doesn't perform any compile time check, but at least at runtime we can be sure that we
                    // are aiming at something that indeed exists within a class.
                    // Note: methods listed in the order they are invoked during flow execution.
                    arrayOf<Any>(TerminationData(RPCServer::class.java.declaredMethods.single { it.name == "context" }, 3)),
                    arrayOf<Any>(TerminationData(ActionExecutorImpl::class.java.declaredMethods.single { it.name == "executeCreateTransaction" }, 4)),
                    arrayOf<Any>(TerminationData(ActionExecutorImpl::class.java.declaredMethods.single { it.name == "executePersistCheckpoint" }, 4)),
                    arrayOf<Any>(TerminationData(ActionExecutorImpl::class.java.declaredMethods.single { it.name == "executePersistDeduplicationIds" }, 4)),
                    arrayOf<Any>(TerminationData(OnLedgerAsset.Companion::class.java.declaredMethods.single { it.name == "gatherCoins" }, 4)),
                    arrayOf<Any>(TerminationData(ActionExecutorImpl::class.java.declaredMethods.single { it.name == "executeRemoveCheckpoint" }, 4)),
                    arrayOf<Any>(TerminationData(ActionExecutorImpl::class.java.declaredMethods.single { it.name == "executeReleaseSoftLocks" }, 4)),
                    arrayOf<Any>(TerminationData(ActionExecutorImpl::class.java.declaredMethods.single { it.name == "executeCommitTransaction" }, 4)),
                    arrayOf<Any>(TerminationData(ClientMessageImpl::class.java.methods.single { it.name == "acknowledge" && !it.isSynthetic }, 4))
            )
        }

        private val testUser = User("test", "test", permissions = setOf(
                Permissions.startFlow<CashIssueFlow>(),
                Permissions.startFlow<CashPaymentFlow>(),
                Permissions.invokeRpc(CordaRPCOps::nodeInfo),
                Permissions.invokeRpc(CordaRPCOps::stateMachineRecordedTransactionMappingSnapshot),
                @Suppress("DEPRECATION")
                Permissions.invokeRpc(CordaRPCOps::internalFindVerifiedTransaction))
        )

        private fun connectRpc(node: NodeHandle): CordaRPCOps {
            val client = CordaRPCClient(node.rpcAddress)
            return client.start(testUser.username, testUser.password).proxy
        }
    }

    @After
    fun shutdown() {
        pollExecutor.shutdown()
    }

    private fun setup(testBlock: InternalDriverDSL.() -> Unit) {

        val portAllocation = PortAllocation.Incremental(10000)

        internalDriver(
                extraCordappPackagesToScan = listOf("net.corda.finance.contracts", "net.corda.finance.schemas"),
                portAllocation = portAllocation,
                inMemoryDB = false
                //, isDebug = true
        ) {
            bytemanPort = portAllocation.nextPort()
            alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(testUser), bytemanPort = bytemanPort).getOrThrow()
            raftNotaryIdentity = defaultNotaryIdentity

            aliceProxy = connectRpc(alice)

            testBlock()
        }
    }

    @Test
    fun testExists() {
        assertNotNull(terminationData.terminationTarget)
    }

    private fun issueCash(amount: Amount<Currency>) {
        aliceProxy.startFlow(::CashIssueFlow, amount, OpaqueBytes.of(0), raftNotaryIdentity).returnValue.getOrThrow()
    }

    private fun paySelf(amount: Amount<Currency>) = aliceProxy.startFlow(::CashPaymentFlow, amount, alice.nodeInfo.singleIdentity()).returnValue.getOrThrow()

    @Test
    fun testNodeRestart() {
        setup {

            val submit = Submit("localhost", bytemanPort)

            val ruleText = """
RULE Create Counter
CLASS net.corda.node.services.messaging.RPCServer
METHOD clientArtemisMessageHandler
AT ENTRY
IF createCounter("paymentCounter", ${terminationData.counterValue})
DO debug("Counter created")
ENDRULE

RULE Decrement Counter
CLASS net.corda.node.services.messaging.RPCServer
METHOD sendReply
AT EXIT
IF TRUE
DO decrementCounter("paymentCounter"); debug("Current counter value: " + readCounter("paymentCounter"))
ENDRULE

RULE Conditionally kill on particular method
CLASS ${terminationData.terminationTarget.declaringClass.name}
METHOD ${terminationData.terminationTarget.name}
AT ENTRY
IF readCounter("paymentCounter") == 0
DO debug("Killing JVM now!"); killJVM()
ENDRULE
"""
            logger.info("For '${terminationData.terminationTarget}', rule is composed as: $ruleText")

            val deploymentOutcome = submit.addScripts(listOf(ScriptText("Restart script for ${terminationData.terminationTarget}", ruleText)))
            Assertions.assertThat(deploymentOutcome).contains("install rule Conditionally kill on particular method")
            Assertions.assertThat(submit.listAllRules()).contains("killJVM")

            // Issue 100 pounds
            issueCash(100.POUNDS)

            logger.info("Cash successfully issued")

            // Submit 2 successful payments
            val successfulPayments = (1..2).map { paySelf(5.POUNDS) }

            logger.info("2 payments successfully made")

            // 3rd payment should trigger JVM termination mid-flight
            Assertions.assertThatThrownBy { paySelf(6.POUNDS) }.isInstanceOf(RPCException::class.java).hasMessageContaining("Connection failure detected")

            logger.info("3rd payment successfully triggered JVM termination")

            // Alice node should no longer be responsive or alive
            assertFalse((alice as OutOfProcess).process.isAlive)
            Assertions.assertThatThrownBy { aliceProxy.nodeInfo() }.isInstanceOf(RPCException::class.java).hasMessageContaining("RPC server is not available")

            logger.info("Node confirmed to be down successfully")

            // Restart node
            alice.stop() // this should perform un-registration in the NetworkMap
            alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(testUser), bytemanPort = bytemanPort).getOrThrow()
            aliceProxy = connectRpc(alice)

            logger.info("Node re-started successfully")

            // Apply poll construct as the node may still be catching-up and processing unfinished flows.
            poll(pollExecutor, "All transactions available post restart") {

                // Check that all 4 transactions are present
                // There are 4 transactions because: 1 Cash issuance, 2 * fully processed payments, 1 payment that been restarted mid-flight
                val snapshot = aliceProxy.stateMachineRecordedTransactionMappingSnapshot()
                if(snapshot.size < 4) {
                    null
                } else {
                    @Suppress("DEPRECATION")
                    val transactions = snapshot.mapNotNull { aliceProxy.internalFindVerifiedTransaction(it.transactionId) }
                    assertEquals(4, snapshot.size)
                    assertEquals(4, transactions.size)
                    Assertions.assertThat(snapshot.map { it.transactionId }.toSet()).containsAll(successfulPayments.map { it.stx.id })

                    val groupedByAmount = transactions.groupBy { (it.coreTransaction.outputStates.first() as Cash.State).amount.withoutIssuer() }
                    assertEquals(1, groupedByAmount[100.POUNDS]!!.size)
                    assertEquals(2, groupedByAmount[5.POUNDS]!!.size)
                    assertEquals(1, groupedByAmount[6.POUNDS]!!.size)

                    logger.info("All 4 transactions present")
                }
            }

            // Make an extra payment to ensure that node is operational
            val anotherPaymentOutcome = paySelf(7.POUNDS)
            assertEquals(7.POUNDS, (anotherPaymentOutcome.stx.tx.outputStates.first() as Cash.State).amount.withoutIssuer())

            logger.info("Additional (4th) transaction posted successfully")
        }
    }
}
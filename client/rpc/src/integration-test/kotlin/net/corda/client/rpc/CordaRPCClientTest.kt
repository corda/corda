package net.corda.client.rpc

import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowInitiator
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashException
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.nodeapi.User
import net.corda.testing.ALICE
import net.corda.testing.chooseIdentity
import net.corda.testing.node.NodeBasedTest
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaRPCClientTest : NodeBasedTest(listOf("net.corda.finance.contracts")) {
    private val rpcUser = User("user1", "test", permissions = setOf(
            startFlowPermission<CashIssueFlow>(),
            startFlowPermission<CashPaymentFlow>()
    ))
    private lateinit var node: StartedNode<Node>
    private lateinit var client: CordaRPCClient
    private var connection: CordaRPCConnection? = null

    private fun login(username: String, password: String) {
        connection = client.start(username, password)
    }

    @Before
    fun setUp() {
        node = startNotaryNode(ALICE.name, rpcUsers = listOf(rpcUser), customSchemas = setOf(CashSchemaV1)).getOrThrow()
        client = CordaRPCClient(node.internals.configuration.rpcAddress!!)
    }

    @After
    fun done() {
        connection?.close()
    }

    @Test
    fun `log in with valid username and password`() {
        login(rpcUser.username, rpcUser.password)
    }

    @Test
    fun `log in with unknown user`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            login(random63BitValue().toString(), rpcUser.password)
        }
    }

    @Test
    fun `log in with incorrect password`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            login(rpcUser.username, random63BitValue().toString())
        }
    }

    @Test
    fun `close-send deadlock and premature shutdown on empty observable`() {
        println("Starting client")
        login(rpcUser.username, rpcUser.password)
        println("Creating proxy")
        println("Starting flow")
        val flowHandle = connection!!.proxy.startTrackedFlow(::CashIssueFlow,
                20.DOLLARS, OpaqueBytes.of(0), node.info.chooseIdentity()
        )
        println("Started flow, waiting on result")
        flowHandle.progress.subscribe {
            println("PROGRESS $it")
        }
        println("Result: ${flowHandle.returnValue.getOrThrow()}")
    }

    @Test
    fun `sub-type of FlowException thrown by flow`() {
        login(rpcUser.username, rpcUser.password)
        val handle = connection!!.proxy.startFlow(::CashPaymentFlow, 100.DOLLARS, node.info.chooseIdentity())
        assertThatExceptionOfType(CashException::class.java).isThrownBy {
            handle.returnValue.getOrThrow()
        }
    }

    @Test
    fun `check basic flow has no progress`() {
        login(rpcUser.username, rpcUser.password)
        connection!!.proxy.startFlow(::CashPaymentFlow, 100.DOLLARS, node.info.chooseIdentity()).use {
            assertFalse(it is FlowProgressHandle<*>)
        }
    }

    @Test
    fun `get cash balances`() {
        login(rpcUser.username, rpcUser.password)
        val proxy = connection!!.proxy
        val startCash = proxy.getCashBalances()
        assertTrue(startCash.isEmpty(), "Should not start with any cash")

        val flowHandle = proxy.startFlow(::CashIssueFlow,
                123.DOLLARS, OpaqueBytes.of(0), node.info.chooseIdentity()
        )
        println("Started issuing cash, waiting on result")
        flowHandle.returnValue.get()

        val cashDollars = proxy.getCashBalance(USD)
        println("Balance: $cashDollars")
        assertEquals(123.DOLLARS, cashDollars)
    }

    @Test
    fun `flow initiator via RPC`() {
        login(rpcUser.username, rpcUser.password)
        val proxy = connection!!.proxy
        var countRpcFlows = 0
        var countShellFlows = 0
        proxy.stateMachinesFeed().updates.subscribe {
            if (it is StateMachineUpdate.Added) {
                val initiator = it.stateMachineInfo.initiator
                if (initiator is FlowInitiator.RPC)
                    countRpcFlows++
                if (initiator is FlowInitiator.Shell)
                    countShellFlows++
            }
        }
        val nodeIdentity = node.info.chooseIdentity()
        node.services.startFlow(CashIssueFlow(2000.DOLLARS, OpaqueBytes.of(0), nodeIdentity), FlowInitiator.Shell).resultFuture.getOrThrow()
        proxy.startFlow(::CashIssueFlow,
                123.DOLLARS,
                OpaqueBytes.of(0),
                nodeIdentity
        ).returnValue.getOrThrow()
        proxy.startFlowDynamic(CashIssueFlow::class.java,
                1000.DOLLARS,
                OpaqueBytes.of(0),
                nodeIdentity).returnValue.getOrThrow()
        assertEquals(2, countRpcFlows)
        assertEquals(1, countShellFlows)
    }
}

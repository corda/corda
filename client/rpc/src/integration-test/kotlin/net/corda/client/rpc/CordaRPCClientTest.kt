package net.corda.client.rpc

import net.corda.core.contracts.DOLLARS
import net.corda.core.flows.FlowException
import net.corda.core.getOrThrow
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startFlowWithProgress
import net.corda.core.node.services.ServiceInfo
import net.corda.core.random63BitValue
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.ALICE
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.node.internal.Node
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.testing.node.NodeBasedTest
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.io.use
import kotlin.test.*

class CordaRPCClientTest : NodeBasedTest() {
    private val rpcUser = User("user1", "test", permissions = setOf(
            startFlowPermission<CashIssueFlow>(),
            startFlowPermission<CashPaymentFlow>()
    ))
    private lateinit var node: Node
    private lateinit var client: CordaRPCClient

    @Before
    fun setUp() {
        node = startNode(ALICE.name, rpcUsers = listOf(rpcUser), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type))).getOrThrow()
        client = CordaRPCClient(node.configuration.rpcAddress!!)
    }

    @After
    fun done() {
        client.close()
    }

    @Test
    fun `log in with valid username and password`() {
        client.start(rpcUser.username, rpcUser.password)
    }

    @Test
    fun `log in with unknown user`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            client.start(random63BitValue().toString(), rpcUser.password)
        }
    }

    @Test
    fun `log in with incorrect password`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            client.start(rpcUser.username, random63BitValue().toString())
        }
    }

    @Test
    fun `close-send deadlock and premature shutdown on empty observable`() {
        println("Starting client")
        client.start(rpcUser.username, rpcUser.password)
        println("Creating proxy")
        val proxy = client.proxy()
        println("Starting flow")
        val flowHandle = proxy.startFlowWithProgress(
                ::CashIssueFlow,
                20.DOLLARS, OpaqueBytes.of(0), node.info.legalIdentity, node.info.legalIdentity)
        println("Started flow, waiting on result")
        flowHandle.progress.subscribe {
            println("PROGRESS $it")
        }
        println("Result: ${flowHandle.returnValue.getOrThrow()}")
    }

    @Test
    fun `FlowException thrown by flow`() {
        client.start(rpcUser.username, rpcUser.password)
        val proxy = client.proxy()
        val handle = proxy.startFlow(::CashPaymentFlow, 100.DOLLARS, node.info.legalIdentity)
        // TODO Restrict this to CashException once RPC serialisation has been fixed
        assertThatExceptionOfType(FlowException::class.java).isThrownBy {
            handle.returnValue.getOrThrow()
        }
    }

    @Test
    fun `check basic flow has no progress`() {
        client.start(rpcUser.username, rpcUser.password)
        val proxy = client.proxy()
        proxy.startFlow(::CashPaymentFlow, 100.DOLLARS, node.info.legalIdentity).use {
            assertFalse(it is FlowProgressHandle<*>)
            assertTrue(it is FlowHandle<*>)
        }
    }

    @Test
    fun `get cash balances`() {
        println("Starting client")
        client.start(rpcUser.username, rpcUser.password)
        println("Creating proxy")
        val proxy = client.proxy()

        val startCash = proxy.getCashBalances()
        assertTrue(startCash.isEmpty(), "Should not start with any cash")

        val flowHandle = proxy.startFlow(::CashIssueFlow,
                123.DOLLARS, OpaqueBytes.of(0),
                node.info.legalIdentity, node.info.legalIdentity
        )
        println("Started issuing cash, waiting on result")
        flowHandle.returnValue.get()

        val finishCash = proxy.getCashBalances()
        println("Cash Balances: $finishCash")
        assertEquals(1, finishCash.size)
        assertEquals(123.DOLLARS, finishCash.get(Currency.getInstance("USD")))
    }

}

package net.corda.test.node

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.internal.packageName
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.internal.Node
import net.corda.node.services.Permissions
import net.corda.node.services.messaging.P2PMessagingClient
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.User
import net.corda.testing.node.internal.NodeBasedTest
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions
import org.junit.Test
import java.util.*
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class NodeSuspendAndResumeTest : NodeBasedTest(listOf("net.corda.finance.contracts", CashSchemaV1::class.packageName)) {

    private val rpcUser = User("user1", "test", permissions = setOf(
            Permissions.startFlow<CashIssueFlow>(),
            Permissions.startFlow<CashPaymentFlow>(),
            Permissions.invokeRpc("vaultQueryBy"),
            Permissions.invokeRpc(CordaRPCOps::stateMachinesFeed),
            Permissions.invokeRpc("vaultQueryByCriteria"))
    )

    @Test
    fun `start suspend resume`() {
        val startedNode = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        val node = startedNode.internals
        (startedNode.network as P2PMessagingClient).runningFuture.get()

        for (i in 1..10) {
            node.suspend()
            node.resume()
            thread(name = ALICE_NAME.organisation) {
                node.run()
            }
            (startedNode.network as P2PMessagingClient).runningFuture.get()
        }
    }

    @Test
    fun `start suspend resume issuing cash`() {
        val startedNode = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        val node = startedNode.internals
        (startedNode.network as P2PMessagingClient).runningFuture.get()

        for (i in 1..10) {
            node.suspend()
            node.resume()
            thread(name = ALICE_NAME.organisation) {
                node.run()
            }
            (startedNode.network as P2PMessagingClient).runningFuture.get()

            issueCash(node, startedNode.info.identityFromX500Name(ALICE_NAME))
            val currentCashAmount = getCashBalance(node)
            println("Balance: $currentCashAmount")
            assertEquals((123 * i).DOLLARS, currentCashAmount)
        }
    }

    @Test
    fun `cash not issued when suspended`() {
        val startedNode = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        val node = startedNode.internals
        (startedNode.network as P2PMessagingClient).runningFuture.get()

        issueCash(node, startedNode.info.identityFromX500Name(ALICE_NAME))

        var currentCashAmount = getCashBalance(node)
        println("Balance: $currentCashAmount")
        assertEquals(123.DOLLARS, currentCashAmount)

        node.suspend()
        Assertions.assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            issueCash(node, startedNode.info.identityFromX500Name(ALICE_NAME))
        }

        node.resume()
        thread(name = ALICE_NAME.organisation) {
            node.run()
        }
        (startedNode.network as P2PMessagingClient).runningFuture.get()

        currentCashAmount = getCashBalance(node)
        println("Balance: $currentCashAmount")
        assertEquals(123.DOLLARS, currentCashAmount)
    }

    @Test
    fun `initialise node without starting`() {
        val node = initNode(ALICE_NAME, rpcUsers = listOf(rpcUser))

        // The node hasn't been started yet
        Assertions.assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
            issueCash(node, node.generateAndSaveNodeInfo().identityFromX500Name(ALICE_NAME))
        }

        node.start()
        thread(name = ALICE_NAME.organisation) {
            node.run()
        }
        (node.started!!.network as P2PMessagingClient).runningFuture.get()

        issueCash(node, node.started!!.info.identityFromX500Name(ALICE_NAME))

        val currentCashAmount = getCashBalance(node)
        println("Balance: $currentCashAmount")
        assertEquals(123.DOLLARS, currentCashAmount)
    }

    @Test
    fun `resume called on node not previously started`() {
        val node = initNode(ALICE_NAME, rpcUsers = listOf(rpcUser))

        // will start the node
        node.resume()

        thread(name = ALICE_NAME.organisation) {
            node.run()
        }
        (node.started!!.network as P2PMessagingClient).runningFuture.get()

        issueCash(node, node.started!!.info.identityFromX500Name(ALICE_NAME))

        val currentCashAmount = getCashBalance(node)
        println("Balance: $currentCashAmount")
        assertEquals(123.DOLLARS, currentCashAmount)
    }

    @Test
    fun `resume called when node not suspended`() {
        val startedNode = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        val node = startedNode.internals

        node.stop()
        node.resume()
        node.resume()

        thread(name = ALICE_NAME.organisation) {
            node.run()
        }
        (node.started!!.network as P2PMessagingClient).runningFuture.get()

        issueCash(node, node.started!!.info.identityFromX500Name(ALICE_NAME))

        val currentCashAmount = getCashBalance(node)
        println("Balance: $currentCashAmount")
        assertEquals(123.DOLLARS, currentCashAmount)
    }

    @Test
    fun `resume called on started node`() {
        val node = initNode(ALICE_NAME, rpcUsers = listOf(rpcUser))

        node.start()
        node.resume()

        thread(name = ALICE_NAME.organisation) {
            node.run()
        }
        (node.started!!.network as P2PMessagingClient).runningFuture.get()

        issueCash(node, node.started!!.info.identityFromX500Name(ALICE_NAME))

        val currentCashAmount = getCashBalance(node)
        println("Balance: $currentCashAmount")
        assertEquals(123.DOLLARS, currentCashAmount)
    }

    @Test
    fun `suspend called when node not started`() {
        val startedNode = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        val node = startedNode.internals

        node.stop()
        node.suspend()

        Assertions.assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
            issueCash(node, node.generateAndSaveNodeInfo().identityFromX500Name(ALICE_NAME))
        }

        node.suspend()

        Assertions.assertThatExceptionOfType(ActiveMQNotConnectedException::class.java).isThrownBy {
            issueCash(node, node.generateAndSaveNodeInfo().identityFromX500Name(ALICE_NAME))
        }
    }

    private fun issueCash(node: Node, party: Party) {
        val client = CordaRPCClient(node.configuration.rpcOptions.address!!)
        val connection = client.start(rpcUser.username, rpcUser.password)
        val proxy = connection.proxy
        val flowHandle = proxy.startFlow(::CashIssueFlow, 123.DOLLARS, OpaqueBytes.of(0), party)
        println("Started issuing cash, waiting on result")
        flowHandle.returnValue.get()

        val cashDollars = proxy.getCashBalance(USD)
        println("Balance: $cashDollars")
        connection.close()
    }

    private fun getCashBalance(node: Node): Amount<Currency> {
        val client = CordaRPCClient(node.configuration.rpcOptions.address!!)
        val connection = client.start(rpcUser.username, rpcUser.password)
        val proxy = connection.proxy
        val cashBalance = proxy.getCashBalance(USD)
        connection.close()
        return cashBalance
    }
}
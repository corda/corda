package net.corda.client

import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.random63BitValue
import net.corda.core.serialization.OpaqueBytes
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.node.driver.DriverBasedTest
import net.corda.node.driver.NodeHandle
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class CordaRPCClientTest : DriverBasedTest() {
    private val rpcUser = User("user1", "test", permissions = setOf(startFlowPermission<CashFlow>()))
    private lateinit var node: NodeHandle
    private lateinit var client: CordaRPCClient

    override fun setup() = driver(isDebug = true) {
        node = startNode(rpcUsers = listOf(rpcUser), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type))).getOrThrow()
        client = node.rpcClientToNode()
        runTest()
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
    fun `indefinite block bug`() {
        println("Starting client")
        client.start(rpcUser.username, rpcUser.password)
        println("Creating proxy")
        val proxy = client.proxy()
        println("Starting flow")
        val flowHandle = proxy.startFlow(
                ::CashFlow,
                CashCommand.IssueCash(20.DOLLARS, OpaqueBytes.of(0), node.nodeInfo.legalIdentity, node.nodeInfo.legalIdentity))
        println("Started flow, waiting on result")
        flowHandle.progress.subscribe {
            println("PROGRESS $it")
        }
        println("Result: ${flowHandle.returnValue.toBlocking().first()}")
    }
}

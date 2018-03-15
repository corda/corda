package net.corda.behave.service.proxy

import net.corda.behave.network.Network
import net.corda.behave.node.Distribution
import net.corda.behave.node.configuration.NotaryType
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueFlow
import org.junit.*

class CordaRPCProxyClientTest {

    companion object {
        private val RPC_PROXY_SERVER_PORT = 13002
        private val rpcProxyHostAndPort = NetworkHostAndPort("localhost", RPC_PROXY_SERVER_PORT)

        private lateinit var network : Network

        @BeforeClass @JvmStatic fun setUp() {
            network = Network.new().addNode(name = "Foo", notaryType = NotaryType.NON_VALIDATING, withRPCProxy = true).generate()
            network.start()
            network.waitUntilRunning()
        }

        @AfterClass @JvmStatic fun tearDown() {
            network.stop()
        }
    }

    @Test
    fun startFlowDynamic() {
        val rpcProxyClient = CordaRPCProxyClient(rpcProxyHostAndPort)
        val notary = rpcProxyClient.notaryIdentities()[0]
        val response = rpcProxyClient.startFlow(::CashIssueFlow, POUNDS(100), OpaqueBytes.of(1), notary)
        println(response)
    }

    @Test
    fun nodeInfo() {
        val rpcProxyClient = CordaRPCProxyClient(rpcProxyHostAndPort)
        val response = rpcProxyClient.nodeInfo()
        println(response)
    }

    @Test
    fun notaryIdentities() {
        val rpcProxyClient = CordaRPCProxyClient(rpcProxyHostAndPort)
        val response = rpcProxyClient.notaryIdentities()
        println(response)
    }

    @Test
    fun registeredFlows() {
        val rpcProxyClient = CordaRPCProxyClient(rpcProxyHostAndPort)
        val response = rpcProxyClient.registeredFlows()
        println(response)
    }
}
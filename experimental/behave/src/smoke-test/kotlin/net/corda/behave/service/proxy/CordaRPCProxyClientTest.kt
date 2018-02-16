package net.corda.behave.service.proxy

import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueFlow
import org.assertj.core.api.Assertions
import org.junit.Test

abstract class CordaRPCProxyClientTest {

    companion object {
        private val RPC_PROXY_SERVER_PORT = 13002
        private val rpcProxyHostAndPort = NetworkHostAndPort("localhost", RPC_PROXY_SERVER_PORT)
    }

    @Test
    fun startFlowDynamic() {
        val rpcProxyClient = CordaRPCProxyClient(rpcProxyHostAndPort)
        val notary = rpcProxyClient.notaryIdentities()[0]
        val response = rpcProxyClient.startFlow(::CashIssueFlow, POUNDS(100), OpaqueBytes.of(1), notary).returnValue.getOrThrow()
        println(response)
        Assertions.assertThat(response.stx.toString()).matches("SignedTransaction\\(id=.*\\)")
    }

    @Test
    fun nodeInfo() {
        val rpcProxyClient = CordaRPCProxyClient(rpcProxyHostAndPort)
        val response = rpcProxyClient.nodeInfo()
        println(response)
        Assertions.assertThat(response.toString()).matches("NodeInfo\\(addresses=\\[.*\\], legalIdentitiesAndCerts=\\[.*\\], platformVersion=.*, serial=.*\\)")
    }

    @Test
    fun notaryIdentities() {
        val rpcProxyClient = CordaRPCProxyClient(rpcProxyHostAndPort)
        val response = rpcProxyClient.notaryIdentities()
        println(response)
        Assertions.assertThat(response.first().name.toString()).isEqualTo("O=Notary, L=London, C=GB")
    }

    @Test
    fun registeredFlows() {
        val rpcProxyClient = CordaRPCProxyClient(rpcProxyHostAndPort)
        val response = rpcProxyClient.registeredFlows()
        println(response)
        // Node built-in flows
        Assertions.assertThat(response).contains("net.corda.core.flows.ContractUpgradeFlow\$Authorise",
                "net.corda.core.flows.ContractUpgradeFlow\$Deauthorise",
                "net.corda.core.flows.ContractUpgradeFlow\$Initiate")
    }
}
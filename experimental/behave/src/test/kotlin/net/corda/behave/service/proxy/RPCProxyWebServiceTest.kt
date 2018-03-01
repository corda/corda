package net.corda.behave.service.proxy

import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.responseAs
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import org.junit.Assert.assertTrue
import org.junit.Test

class RPCProxyWebServiceTest {

//    @Rule
//    @JvmField
//    val testSerialization = SerializationEnvironmentRule(true)

    private val hostAndPort = NetworkHostAndPort("localhost", 13002)
    private val rpcProxyClient = CordaRPCProxyClient(hostAndPort)

    @Test
    fun myIp() {
//        RPCProxyServer(hostAndPort, webService = RPCProxyWebService()).use {
//            it.start()
            val response = doGet<String>("my-ip")
            println(response)
            assertTrue(response.contains("My ip is"))
//        }
    }

    @Test
    fun nodeInfo() {
//        RPCProxyServer(hostAndPort, webService = RPCProxyWebService()).use {
//            it.start()
            val response = rpcProxyClient.nodeInfo()
            println(response)
//        }
    }

    @Test
    fun registeredFlows() {
//        RPCProxyServer(hostAndPort, webService = RPCProxyWebService()).use {
//            it.start()
            val response = rpcProxyClient.registeredFlows()
            println(response)
//        }
    }

    @Test
    fun notaryIdentities() {
//        RPCProxyServer(hostAndPort, webService = RPCProxyWebService()).use {
//            it.start()
            val response = rpcProxyClient.notaryIdentities()
            println(response)
//        }
    }

    @Test
    fun networkMapSnapshot() {
        val response = rpcProxyClient.networkMapSnapshot()
        println(response)
    }

    @Test
    fun startFlowCashIssue() {
        val notary = rpcProxyClient.notaryIdentities()[0]
        val response = rpcProxyClient.startFlow(::CashIssueFlow, POUNDS(1000), OpaqueBytes.of(1), notary)
        val result = response.returnValue.getOrThrow().stx
        println(result)
    }

    @Test
    fun startFlowCashPayment() {
        val recipient = rpcProxyClient.partiesFromName("EntityB", false).first()
        val response = rpcProxyClient.startFlow(::CashPaymentFlow, POUNDS(100), recipient)
        val result = response.returnValue.getOrThrow().stx
        println(result)
    }

    @Test
    fun startFlowCashExit() {
        val response = rpcProxyClient.startFlow(::CashExitFlow, POUNDS(500), OpaqueBytes.of(1))
        val result = response.returnValue.getOrThrow().stx
        println(result)
    }

    @Test
    fun vaultQueryCash() {
        val response = rpcProxyClient.vaultQuery(Cash.State::class.java)
        response.states.forEach { state ->
            println("${state.state.data.amount}")
        }
    }

    private inline fun <reified T : Any> doGet(path: String): T {
        return java.net.URL("http://$hostAndPort/rpc/$path").openHttpConnection().responseAs()
    }
}
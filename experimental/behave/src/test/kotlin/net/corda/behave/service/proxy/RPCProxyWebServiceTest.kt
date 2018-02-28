package net.corda.behave.service.proxy

import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.responseAs
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.POUNDS
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
        RPCProxyServer(hostAndPort, webService = RPCProxyWebService()).use {
            it.start()
            val response = doGet<String>("my-ip")
            println(response)
            assertTrue(response.contains("My ip is"))
        }
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
    fun startFlowCashIssue() {
//        RPCProxyServer(hostAndPort, webService = RPCProxyWebService()).use {
//            it.start()

        val notary = rpcProxyClient.notaryIdentities()[0]
        val response = rpcProxyClient.startFlow(::CashIssueFlow, POUNDS(100), OpaqueBytes.of(1), notary)
            println(response)
//        }
    }

    @Test
    fun startFlowCashPayment() {
//        RPCProxyServer(hostAndPort, webService = RPCProxyWebService()).use {
//            it.start()
        val notary = rpcProxyClient.notaryIdentities()[0]
        val response = rpcProxyClient.startFlow(::CashPaymentFlow, POUNDS(100), notary)
        println(response)
//        }
    }

    private inline fun <reified T : Any> doGet(path: String): T {
        return java.net.URL("http://$hostAndPort/rpc/$path").openHttpConnection().responseAs()
    }
}
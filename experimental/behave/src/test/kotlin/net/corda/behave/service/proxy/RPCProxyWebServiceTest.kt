package net.corda.behave.service.proxy

import com.opengamma.strata.product.common.BuySell
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
import net.corda.vega.api.SwapDataModel
import net.corda.vega.flows.IRSTradeFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RPCProxyWebServiceTest {

//    @Rule
//    @JvmField
//    val testSerialization = SerializationEnvironmentRule(true)

    private val hostAndPort = NetworkHostAndPort("localhost", 13002)
    private val rpcProxyClient = CordaRPCProxyClient(hostAndPort)

    private val hostAndPortB = NetworkHostAndPort("localhost", 13007)
    private val rpcProxyClientB = CordaRPCProxyClient(hostAndPortB)

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
    fun startFlowSIMMTrade() {
        val valuationDate: LocalDate = LocalDate.parse("2016-06-06")
        val tradeId = "trade1"

        val swap = SwapDataModel(tradeId, "desc", valuationDate, "EUR_FIXED_1Y_EURIBOR_3M",
                valuationDate, LocalDate.parse("2020-01-02"), BuySell.BUY, BigDecimal.valueOf(1000), BigDecimal.valueOf(0.1))

        val ownParty = rpcProxyClient.partiesFromName("EntityA", false).first()
        val counterParty = rpcProxyClient.partiesFromName("EntityB", false).first()

        val buyer = if (swap.buySell.isBuy) ownParty else counterParty
        val seller = if (swap.buySell.isSell) ownParty else counterParty
        val response = rpcProxyClient.startFlow(IRSTradeFlow::Requester, swap.toData(buyer, seller), ownParty).returnValue.getOrThrow()
        println(response)
    }

    @Test
    fun vaultQueryCash() {
        try {
            val responseA = rpcProxyClient.vaultQuery(Cash.State::class.java)
            responseA.states.forEach { state ->
                println("Entity A: ${state.state.data.amount}")
            }

            val responseB = rpcProxyClientB.vaultQuery(Cash.State::class.java)
            responseB.states.forEach { state ->
                println("Entity B: ${state.state.data.amount}")
            }
        }
        catch (e: Exception) {
            println("Vault Cash query error: ${e.message}")
        }
    }

    private inline fun <reified T : Any> doGet(path: String): T {
        return java.net.URL("http://$hostAndPort/rpc/$path").openHttpConnection().responseAs()
    }
}
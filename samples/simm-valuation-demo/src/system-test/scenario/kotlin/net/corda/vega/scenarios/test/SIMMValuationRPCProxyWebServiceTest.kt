package net.corda.vega.scenarios.test

import com.opengamma.strata.product.common.BuySell
import net.corda.behave.service.proxy.CordaRPCProxyClient
import net.corda.client.rpc.internal.KryoClientSerializationScheme.Companion.initialiseSerialization
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.vega.api.SwapDataModel
import net.corda.vega.contracts.IRSState
import net.corda.vega.flows.IRSTradeFlow
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

//class RPCProxyWebServiceTest : BaseRPCProxyWebServiceTest {
// Base interface executes a basic subset of public RPC API calls.
class SIMMValuationRPCProxyWebServiceTest {

    init {
        try { initialiseSerialization() } catch (e: Exception) {}
    }

    /**
     *  These tests assume you have a running Corda Network fronted with an HTTPtoRPCProxy (for Kryo -> AMQP bridging)
     *
     *  client --[RPC Kyro wire-format]--> HTTPtoRPCProxy ---[ P2P AMQP wire-format]--> Corda Node
     *
     *  These tests become redundant once we move from Kryo to AMQP for RPC clients.
     */
    private val hostAndPort = NetworkHostAndPort("localhost", 13002)
    private val rpcProxyClient = CordaRPCProxyClient(hostAndPort)

    @Test
    fun startFlowSIMMTrade() {
        val valuationDate: LocalDate = LocalDate.parse("2016-06-06")
        val tradeId = "trade1"

        val swap = SwapDataModel(tradeId, "desc", valuationDate, "EUR_FIXED_1Y_EURIBOR_3M",
                valuationDate, LocalDate.parse("2020-01-02"), BuySell.BUY, BigDecimal.valueOf(1000), BigDecimal.valueOf(0.1))

        val ownParty = rpcProxyClient.partiesFromName("PartyA", false).first()
        val counterParty = rpcProxyClient.partiesFromName("PartyB", false).first()

        val buyer = if (swap.buySell.isBuy) ownParty else counterParty
        val seller = if (swap.buySell.isSell) ownParty else counterParty
        val response = rpcProxyClient.startFlow(IRSTradeFlow::Requester, swap.toData(buyer, seller), ownParty).returnValue.getOrThrow()
        println(response)
    }


    @Test
    fun vaultQuery() {
        try {
            val responseA = rpcProxyClient.vaultQuery(IRSState::class.java)
            responseA.states.forEach { state ->
                println("PartyA: ${state.state.data}")
            }
        }
        catch (e: Exception) {
            println("Vault query error: ${e.message}")
            fail()
        }
    }
}
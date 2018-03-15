package net.corda.behave.service.proxy

import com.opengamma.strata.product.common.BuySell
import net.corda.core.CordaException
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
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
import net.corda.option.base.OptionType
import net.corda.option.base.state.OptionState
import net.corda.option.client.flow.OptionIssueFlow
import net.corda.option.client.flow.OptionTradeFlow
import net.corda.option.client.flow.SelfIssueCashFlow
import net.corda.vega.api.SwapDataModel
import net.corda.vega.flows.IRSTradeFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

class RPCProxyWebServiceTest {

//    @Rule
//    @JvmField
//    val testSerialization = SerializationEnvironmentRule(true)

    private val hostAndPort = NetworkHostAndPort("localhost", 13000)
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
        val recipient = rpcProxyClient.partiesFromName("PartyB", false).first()
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
    fun startFlowOptionsCash() {
        val response = rpcProxyClient.startFlow(::SelfIssueCashFlow, 50000.POUNDS).returnValue.getOrThrow()
        println(response)
    }

    @Test
    fun startFlowOptionsIssue() {
        val ownParty = rpcProxyClient.partiesFromName("PartyA", false).first()
        val issuerParty = rpcProxyClient.partiesFromName("Issuer", false).firstOrNull() ?: throw IllegalArgumentException("Unknown issuer")
        val expiryDate = LocalDate.parse("2022-04-12").atStartOfDay().toInstant(ZoneOffset.UTC)
        val type =OptionType.CALL
        val strikePrice = 95.POUNDS
        val underlying = "Wilburton State Bank"
        val optionToIssue = OptionState(
                strikePrice = strikePrice,
                expiryDate = expiryDate,
                underlyingStock = underlying,
                issuer = issuerParty,
                owner = ownParty,
                optionType = type)
        val response = rpcProxyClient.startFlow(OptionIssueFlow::Initiator, optionToIssue).returnValue.getOrThrow()
        println(response)
    }

    @Test
    fun startFlowOptionsTrade() {
        val trade = rpcProxyClient.vaultQuery(OptionState::class.java).states.firstOrNull() ?: throw CordaException("No states in vault")
        val counterparty = rpcProxyClient.partiesFromName("PartyB", false).firstOrNull() ?: throw IllegalArgumentException("Unknown counterparty")
        println("Trading ${trade.state.data} with counterparty $counterparty")
        val response = rpcProxyClient.startFlow(OptionTradeFlow::Initiator, trade.state.data.linearId, counterparty).returnValue.getOrThrow()
        println(response)
    }

    @Test
    fun vaultQueryCash() {
        try {
            val responseA = rpcProxyClient.vaultQuery(Cash.State::class.java)
            responseA.states.forEach { state ->
                println("PartyA: ${state.state.data.amount}")
            }

            val responseB = rpcProxyClientB.vaultQuery(Cash.State::class.java)
            responseB.states.forEach { state ->
                println("Party B: ${state.state.data.amount}")
            }
        }
        catch (e: Exception) {
            println("Vault Cash query error: ${e.message}")
        }
    }

    private fun <T: ContractState> vaultQuery(contractStateType: Class<out T>): List<StateAndRef<T>> {
        try {
            return rpcProxyClient.vaultQuery(contractStateType).states
        }
        catch (e: Exception) {
            println("Vault query error: ${e.message}")
            throw e
        }
    }

    private inline fun <reified T : Any> doGet(path: String): T {
        return java.net.URL("http://$hostAndPort/rpc/$path").openHttpConnection().responseAs()
    }
}
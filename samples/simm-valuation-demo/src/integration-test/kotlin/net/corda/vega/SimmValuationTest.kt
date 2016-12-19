package net.corda.vega

import com.opengamma.strata.product.common.BuySell
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.NodeHandle
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.IntegrationTestCategory
import net.corda.testing.getHostAndPort
import net.corda.testing.http.HttpApi
import net.corda.vega.api.PortfolioApi
import net.corda.vega.api.PortfolioApiUtils
import net.corda.vega.api.SwapDataModel
import net.corda.vega.api.SwapDataView
import net.corda.vega.portfolio.Portfolio
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

class SimmValuationTest: IntegrationTestCategory {
    private companion object {
        // SIMM demo can only currently handle one valuation date due to a lack of market data or a market data source.
        val valuationDate = LocalDate.parse("2016-06-06")
        val nodeALegalName = "Bank A"
        val nodeBLegalName = "Bank B"
        val testTradeId = "trade1"
    }

    @Test fun `runs SIMM valuation demo`() {
        driver(isDebug = true) {
            startNode("Controller", setOf(ServiceInfo(SimpleNotaryService.type))).getOrThrow()
            val nodeA = getSimmNodeApi(startNode(nodeALegalName))
            val nodeB = getSimmNodeApi(startNode(nodeBLegalName))
            val nodeBParty = getPartyWithName(nodeA, nodeBLegalName)
            val nodeAParty = getPartyWithName(nodeB, nodeALegalName)

            assert(createTradeBetween(nodeA, nodeBParty, testTradeId))
            assert(tradeExists(nodeB, nodeAParty, testTradeId))
            assert(runValuationsBetween(nodeA, nodeBParty))
            assert(valuationExists(nodeB, nodeAParty))
        }
    }

    private fun getSimmNodeApi(futureNode: Future<NodeHandle>): HttpApi {
        val nodeAddr = futureNode.getOrThrow().config.getHostAndPort("webAddress")
        return HttpApi.fromHostAndPort(nodeAddr, "api/simmvaluationdemo")
    }

    private fun getPartyWithName(node: HttpApi, countryparty: String): PortfolioApi.ApiParty =
            getAvailablePartiesFor(node).counterparties.single { it.text == countryparty }

    private fun getAvailablePartiesFor(api: HttpApi): PortfolioApi.AvailableParties {
        return api.getJson<PortfolioApi.AvailableParties>("whoami")
    }

    private fun createTradeBetween(api: HttpApi, counterparty: PortfolioApi.ApiParty, tradeId: String): Boolean {
        val trade = SwapDataModel(tradeId, "desc", valuationDate, "EUR_FIXED_1Y_EURIBOR_3M",
                valuationDate, LocalDate.parse("2020-01-02"), BuySell.BUY, BigDecimal.valueOf(1000), BigDecimal.valueOf(0.1))
        return api.putJson("${counterparty.id}/trades", trade)
    }

    private fun tradeExists(api: HttpApi, counterparty: PortfolioApi.ApiParty, tradeId: String): Boolean {
        val trades = api.getJson<Array<SwapDataView>>("${counterparty.id}/trades")
        return (trades.find { it.id == tradeId } != null)
    }

    private fun runValuationsBetween(api: HttpApi, counterparty: PortfolioApi.ApiParty): Boolean {
        return api.postJson("${counterparty.id}/portfolio/valuations/calculate", PortfolioApi.ValuationCreationParams(valuationDate))
    }

    private fun valuationExists(api: HttpApi, counterparty: PortfolioApi.ApiParty): Boolean {
        val valuations = api.getJson<PortfolioApiUtils.ValuationsView>("${counterparty.id}/portfolio/valuations")
        return (valuations.initialMargin.call["total"] != 0.0)
    }
}
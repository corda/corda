package net.corda.vega

import com.opengamma.strata.product.common.BuySell
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.IntegrationTestCategory
import net.corda.testing.getHostAndPort
import net.corda.testing.http.HttpApi
import net.corda.vega.api.PortfolioApi
import net.corda.vega.api.SwapDataModel
import net.corda.vega.portfolio.Portfolio
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SimmValuationTest: IntegrationTestCategory {

    @Test fun `runs SIMM valuation demo`() {
        driver(isDebug = true) {
            val nodeBLegalName = "Bank B"
            val controller = startNode("Controller", setOf(ServiceInfo(SimpleNotaryService.type))).getOrThrow()
            val nodeAAddr = startNode("Bank A").getOrThrow().config.getHostAndPort("webAddress")
            val nodeBAddr = startNode(nodeBLegalName).getOrThrow().config.getHostAndPort("webAddress")

            val nodeA = HttpApi.fromHostAndPort(nodeAAddr, "api/simmvaluationdemo")

            val parties = getAvailablePartiesFor(nodeA)
            val nodeB = parties.counterparties.single { it.text == nodeBLegalName}
            assert(createTradeBetween(nodeA, nodeB))
            assert(runValuationsBetween(nodeA, nodeB))
            waitForAllNodesToFinish()
        }
    }

    private fun getAvailablePartiesFor(api: HttpApi): PortfolioApi.AvailableParties {
        return api.getJson<PortfolioApi.AvailableParties>("whoami")
    }

    private fun createTradeBetween(api: HttpApi, counterparty: PortfolioApi.ApiParty): Boolean {
        val trade = SwapDataModel("trade1", "desc", LocalDate.parse("2016-01-01"), "EUR_FIXED_1Y_EURIBOR_3M",
                LocalDate.parse("2016-01-02"), LocalDate.parse("2020-01-02"), BuySell.BUY, BigDecimal.valueOf(1000), BigDecimal.valueOf(0.1))
        return api.putJson("${counterparty.id}/trades", trade)
    }

    private fun runValuationsBetween(api: HttpApi, counterparty: PortfolioApi.ApiParty): Boolean {
        return api.postJson("${counterparty.id}/trade/TODO/valuations/calculate")
    }
}
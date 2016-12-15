package net.corda.vega

import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.node.driver.driver
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.testing.IntegrationTestCategory
import net.corda.testing.getHostAndPort
import net.corda.testing.http.HttpApi
import net.corda.vega.api.PortfolioApi
import net.corda.vega.portfolio.Portfolio
import org.junit.Test

class SimmValuationTest: IntegrationTestCategory {

    @Test fun `runs SIMM valuation demo`() {
        driver(isDebug = true) {
            val nodeBLegalName = "Bank B"
            val controller = startNode("Controller", setOf(ServiceInfo(SimpleNotaryService.type))).getOrThrow()
            val nodeAAddr = startNode("Bank A").getOrThrow().config.getHostAndPort("webAddress")
            val nodeBAddr = startNode(nodeBLegalName).getOrThrow().config.getHostAndPort("webAddress")

            val nodeA = HttpApi.fromHostAndPort(nodeAAddr, "simmvaluationdemo")

            val parties = getAvailablePartiesFor(nodeA)
            val nodeB = parties.counterparties.single { it.text == nodeBLegalName}
            assert(createTradeBetween(nodeA, nodeB))
            assert(runValuationsBetween(nodeA, nodeB))
        }
    }

    private fun getAvailablePartiesFor(api: HttpApi): PortfolioApi.AvailableParties {
        return api.getJson<PortfolioApi.AvailableParties>("whoami")
    }

    // TODO: create, verify, run, verify or determine a better test structure.
    private fun createTradeBetween(api: HttpApi, counterparty: PortfolioApi.ApiParty): Boolean {
        return api.postJson("{$counterparty.id}/trades", mapOf("a" to "a"))
    }

    private fun runValuationsBetween(api: HttpApi, counterparty: PortfolioApi.ApiParty): Boolean {
        return api.postJson("{$counterparty.id/trade/TODO/valuations/calculate")
    }
}
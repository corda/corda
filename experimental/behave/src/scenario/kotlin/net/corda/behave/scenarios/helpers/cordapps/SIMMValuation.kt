package net.corda.behave.scenarios.helpers.cordapps

import com.opengamma.strata.product.common.BuySell
import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.helpers.Substeps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.vega.api.SwapDataModel
import net.corda.vega.flows.IRSTradeFlow
import java.math.BigDecimal
import java.time.LocalDate

class SIMMValuation(state: ScenarioState) : Substeps(state) {

    private companion object {
        // SIMM demo can only currently handle one valuation date due to a lack of market data or a market data source.
        val valuationDate: LocalDate = LocalDate.parse("2016-06-06")
        val tradeId = "trade1"
    }

    fun trade(ownNode: String, counterpartyNode: String) : SignedTransaction {
        return withClientProxy(ownNode) {
            val swap = SwapDataModel(tradeId, "desc", valuationDate, "EUR_FIXED_1Y_EURIBOR_3M",
                    valuationDate, LocalDate.parse("2020-01-02"), BuySell.BUY, BigDecimal.valueOf(1000), BigDecimal.valueOf(0.1))

            val ownParty = it.partiesFromName("EntityA", false).first()
            val counterParty = it.partiesFromName("EntityB", false).first()

            val buyer = if (swap.buySell.isBuy) ownParty else counterParty
            val seller = if (swap.buySell.isSell) ownParty else counterParty
            return@withClientProxy it.startFlow(IRSTradeFlow::Requester, swap.toData(buyer, seller), ownParty).returnValue.getOrThrow()
        }
    }

    fun runValuation(node: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun checkValuation(value: Long?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
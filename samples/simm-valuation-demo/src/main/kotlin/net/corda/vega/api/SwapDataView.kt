package net.corda.vega.api

import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.product.common.BuySell
import net.corda.core.identity.Party
import net.corda.vega.analytics.InitialMarginTriple
import net.corda.vega.contracts.SwapData
import net.corda.vega.portfolio.Portfolio
import java.math.BigDecimal
import java.time.LocalDate

/**
 * View of an IRS trade that is converted to JSON when served.
 */
data class SwapDataView(
        val id: String,
        val product: String,
        val tradeDate: LocalDate,
        val effectiveDate: LocalDate,
        val maturityDate: LocalDate,
        val currency: String,
        val buySell: BuySell,
        val notional: BigDecimal,
        var IM: Double? = null,
        var MTM: Double? = null,
        var margined: Boolean = false,
        var marginedText: String = "❌️")

fun SwapData.toView(viewingParty: Party, portfolio: Portfolio? = null,
                    presentValue: MultiCurrencyAmount? = null,
                    IM: InitialMarginTriple? = null): SwapDataView {
    val isBuyer = viewingParty.owningKey == buyer.second
    val trade = if (isBuyer) toFixedLeg() else toFloatingLeg()
    val leg = getLegForParty(viewingParty)
    val sdv = SwapDataView(
            id.second,
            "Vanilla IRS",
            tradeDate,
            startDate,
            endDate,
            trade.product.legs.first().currency.code,
            leg.buySell,
            notional)
    if (portfolio != null && portfolio.swaps.filter { it.id.second == sdv.id }.any()) {
        sdv.margined = true
        sdv.marginedText = "✔"
        sdv.IM = BigDecimal.ZERO.toDouble()
        if (presentValue != null) {
            val amount = presentValue.amounts.first().amount
            sdv.MTM = if (isBuyer)
                amount
            else
                -amount // TODO: Should be able to display an array ?
        }
        if (IM != null) {
            sdv.IM = IM.third
        }
    }
    return sdv
}

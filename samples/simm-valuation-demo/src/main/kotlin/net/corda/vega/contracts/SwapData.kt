package net.corda.vega.contracts

import com.opengamma.strata.basics.ReferenceData
import com.opengamma.strata.basics.StandardId
import com.opengamma.strata.basics.date.Tenor
import com.opengamma.strata.product.TradeAttributeType
import com.opengamma.strata.product.TradeInfo
import com.opengamma.strata.product.common.BuySell
import com.opengamma.strata.product.swap.SwapTrade
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.toBase58String
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal
import java.security.PublicKey
import java.time.LocalDate

/**
 * A single leg of a trade.
 */
interface Leg {
    val notional: BigDecimal
    val buySell: BuySell
}

/**
 * The buying side of a trade.
 */
data class FixedLeg(val _notional: BigDecimal, override val notional: BigDecimal = _notional, override val buySell: BuySell = BuySell.BUY) : Leg

/**
 * The selling side of a trade.
 */
data class FloatingLeg(val _notional: BigDecimal, override val notional: BigDecimal = -_notional, override val buySell: BuySell = BuySell.SELL) : Leg

/**
 * Represents a swap between two parties, a buyer and a seller. This class is a builder for OpenGamma SwapTrades.
 */
@CordaSerializable
data class SwapData(
        val id: Pair<String, String>,
        val buyer: Pair<String, PublicKey>,
        val seller: Pair<String, PublicKey>,
        val description: String,
        val tradeDate: LocalDate,
        val convention: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val notional: BigDecimal,
        val fixedRate: BigDecimal) {

    fun getLegForParty(party: AbstractParty): Leg {
        return if (party == buyer.second) FixedLeg(notional) else FloatingLeg(notional)
    }

    fun toFixedLeg(): SwapTrade {
        return getTrade(BuySell.BUY, Pair("party", buyer.second))
    }

    fun toFloatingLeg(): SwapTrade {
        return getTrade(BuySell.SELL, Pair("party", seller.second))
    }

    private fun getTrade(buySell: BuySell, party: Pair<String, PublicKey>): SwapTrade {
        val tradeInfo = TradeInfo.builder()
                .id(StandardId.of(id.first, id.second))
                .addAttribute(TradeAttributeType.DESCRIPTION, description)
                .counterparty(StandardId.of(party.first, party.second.toBase58String()))
                .build()
        // TODO: Fix below to be correct - change tenor and reference data
        return getSwapConvention(convention).createTrade(startDate, Tenor.TENOR_4Y, buySell, notional.toDouble(), fixedRate.toDouble(), ReferenceData.standard())
                .toBuilder()
                .info(tradeInfo)
                .build()
    }

    private fun getSwapConvention(name: String): FixedIborSwapConvention {
        return when (name) {
            "USD_FIXED_6M_LIBOR_3M" -> FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M
            "EUR_FIXED_1Y_EURIBOR_3M" -> FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M
            "EUR_FIXED_1Y_EURIBOR_6M" -> FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M
            "USD_FIXED_1Y_LIBOR_3M" -> FixedIborSwapConventions.USD_FIXED_1Y_LIBOR_3M
            "GBP_FIXED_1Y_LIBOR_3M" -> FixedIborSwapConventions.GBP_FIXED_1Y_LIBOR_3M
            "GBP_FIXED_6M_LIBOR_6M" -> FixedIborSwapConventions.GBP_FIXED_6M_LIBOR_6M
            "GBP_FIXED_3M_LIBOR_3M" -> FixedIborSwapConventions.GBP_FIXED_3M_LIBOR_3M
            "CHF_FIXED_1Y_LIBOR_3M" -> FixedIborSwapConventions.CHF_FIXED_1Y_LIBOR_3M
            "CHF_FIXED_1Y_LIBOR_6M" -> FixedIborSwapConventions.CHF_FIXED_1Y_LIBOR_6M
            "JPY_FIXED_6M_TIBORJ_3M" -> FixedIborSwapConventions.JPY_FIXED_6M_TIBORJ_3M
            "JPY_FIXED_6M_LIBOR_6M" -> FixedIborSwapConventions.JPY_FIXED_6M_LIBOR_6M
            else -> throw IllegalArgumentException("Unknown swap convention: $name")
        }
    }
}












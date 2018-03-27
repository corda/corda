package net.corda.vega.contracts

import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import net.corda.annotations.serialization.CordaSerializable
import net.corda.vega.analytics.CordaMarketData
import net.corda.vega.analytics.InitialMarginTriple
import java.math.BigDecimal

/**
 * Contains the valuation inputs and outputs of a portfolio. Currently just represents the initial margin and inputs
 * to SIMM.
 *
 * We have to store trade counts in this object because a history is required and
 * we want to avoid walking the transaction chain.
 */
@CordaSerializable
data class PortfolioValuation(val trades: Int,
                              val notional: BigDecimal,
                              val marketData: CordaMarketData,
                              val totalSensivities: CurrencyParameterSensitivities,
                              val currencySensitivies: MultiCurrencyAmount,
                              val margin: InitialMarginTriple,
                              val imContributionMap: Map<String, InitialMarginTriple>?,
                              val presentValues: Map<String, MultiCurrencyAmount>)

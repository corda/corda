package net.corda.vega.analytics

import com.opengamma.strata.basics.currency.Currency
import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.data.MarketDataFxRateProvider
import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider

/**
 * Stub class for the real BIMM implementation
 */

@Suppress("UNUSED_PARAMETER")
class PortfolioNormalizer(eur: Currency?, combinedRatesProvider: ImmutableRatesProvider?) {}

/**
 * Stub class for the real BIMM implementation
 */
@Suppress("UNUSED_PARAMETER")
class RwamBimmNotProductClassesCalculator(fxRateProvider: MarketDataFxRateProvider?, eur: Currency?, instance: Any) {}

/**
 * Stub class for the real BIMM implementation
 */
@Suppress("UNUSED_PARAMETER")
class IsdaConfiguration {
    object INSTANCE {}
}

/**
 * Stub for the real BIMM implementation
 */
@Suppress("UNUSED_PARAMETER")
object BimmAnalysisUtils {
    fun computeMargin(combinedRatesProvider: ImmutableRatesProvider?, normalizer: PortfolioNormalizer, calculatorTotal: RwamBimmNotProductClassesCalculator,
                      first: CurrencyParameterSensitivities, second: MultiCurrencyAmount): Triple<Double, Double, Double> {
        val amount = (second.amounts.reduce { x, y -> x + y }.amount) / 100.0 // Not to be used for financial purposes...
        return Triple<Double, Double, Double>(amount, 0.0, amount)
    }
}


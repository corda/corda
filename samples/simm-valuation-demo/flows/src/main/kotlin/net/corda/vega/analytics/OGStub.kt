/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
class PortfolioNormalizer(eur: Currency?, combinedRatesProvider: ImmutableRatesProvider?)

/**
 * Stub class for the real BIMM implementation
 */
@Suppress("UNUSED_PARAMETER")
class RwamBimmNotProductClassesCalculator(fxRateProvider: MarketDataFxRateProvider?, eur: Currency?, instance: Any)

/**
 * Stub class for the real BIMM implementation
 */
@Suppress("UNUSED_PARAMETER")
class IsdaConfiguration {
    object INSTANCE
}

/**
 * Stub for the real BIMM implementation
 */
@Suppress("UNUSED_PARAMETER")
object BimmAnalysisUtils {
    fun computeMargin(combinedRatesProvider: ImmutableRatesProvider?, normalizer: PortfolioNormalizer, calculatorTotal: RwamBimmNotProductClassesCalculator,
                      first: CurrencyParameterSensitivities, second: MultiCurrencyAmount): Triple<Double, Double, Double> {

        val amount = second.amounts.map { it.amount }.sum()
        return Triple(amount, 0.0, amount)
    }
}


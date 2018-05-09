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

import com.google.common.collect.ImmutableList
import com.opengamma.strata.basics.currency.Currency
import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.collect.io.ResourceLocator
import com.opengamma.strata.data.ImmutableMarketData
import com.opengamma.strata.data.MarketData
import com.opengamma.strata.data.MarketDataFxRateProvider
import com.opengamma.strata.loader.csv.FxRatesCsvLoader
import com.opengamma.strata.loader.csv.QuotesCsvLoader
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader
import com.opengamma.strata.market.curve.CurveGroupDefinition
import com.opengamma.strata.market.curve.CurveGroupName
import com.opengamma.strata.market.param.CurrencyParameterSensitivities
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer
import com.opengamma.strata.product.swap.ResolvedSwapTrade
import net.corda.core.utilities.contextLogger
import net.corda.vega.flows.toCordaCompatible
import java.time.LocalDate


/**
 * Base class of analytics engine for running analytics on a portfolio.
 */
abstract class AnalyticsEngine {

    data class CurrencyAmount(val currencyParameterSensitivities: CurrencyParameterSensitivities,
                              val multiCurrencyAmount: MultiCurrencyAmount)

    abstract fun curveGroup(): CurveGroupDefinition
    abstract fun marketData(asOf: LocalDate): MarketData
    abstract fun sensitivities(
            trades: List<ResolvedSwapTrade>,
            pricer: DiscountingSwapProductPricer,
            combinedRatesProvider: ImmutableRatesProvider
    ): Pair<CurrencyParameterSensitivities, MultiCurrencyAmount>

    abstract fun margin(combinedRatesProvider: ImmutableRatesProvider,
                        fxRateProvider: MarketDataFxRateProvider,
                        totalSensitivities: CurrencyParameterSensitivities,
                        totalCurrencyExposure: MultiCurrencyAmount): Triple<Double, Double, Double>

    abstract fun calculateSensitivitiesBatch(trades: List<ResolvedSwapTrade>,
                                             pricer: DiscountingSwapProductPricer,
                                             ratesProvider: ImmutableRatesProvider): Map<ResolvedSwapTrade, CurrencyAmount>


    abstract fun calculateMarginBatch(tradeSensitivitiesMap: Map<ResolvedSwapTrade, CurrencyAmount>,
                                      combinedRatesProvider: ImmutableRatesProvider,
                                      fxRateProvider: MarketDataFxRateProvider,
                                      portfolioMargin: InitialMarginTriple): Map<ResolvedSwapTrade, InitialMarginTriple>
}

class OGSIMMAnalyticsEngine : AnalyticsEngine() {
    private companion object {
        private val log = contextLogger()
    }

    override fun curveGroup(): CurveGroupDefinition {
        return loadCurveGroup()
    }

    override fun marketData(asOf: LocalDate): MarketData {
        return loadMarketData(asOf)
    }

    override fun sensitivities(trades: List<ResolvedSwapTrade>, pricer: DiscountingSwapProductPricer, combinedRatesProvider: ImmutableRatesProvider): Pair<CurrencyParameterSensitivities, MultiCurrencyAmount> {
        log.info("Running sensitivities on ${trades.size} trades")

        var totalSensitivities = CurrencyParameterSensitivities.empty()
        var totalCurrencyExposure = MultiCurrencyAmount.empty()
        for (resolvedTrade in trades) {
            val swap = resolvedTrade.product

            val pointSensitivities = pricer.presentValueSensitivity(swap, combinedRatesProvider).build()
            val sensitivities = combinedRatesProvider.parameterSensitivity(pointSensitivities)
            val currencyExposure = pricer.currencyExposure(swap, combinedRatesProvider)

            totalSensitivities = totalSensitivities.combinedWith(sensitivities)
            totalCurrencyExposure = totalCurrencyExposure.plus(currencyExposure)
        }
        return Pair(totalSensitivities, totalCurrencyExposure)
    }

    override fun margin(combinedRatesProvider: ImmutableRatesProvider, fxRateProvider: MarketDataFxRateProvider,
                        totalSensitivities: CurrencyParameterSensitivities,
                        totalCurrencyExposure: MultiCurrencyAmount): Triple<Double, Double, Double> {
        val normalizer = PortfolioNormalizer(Currency.EUR, combinedRatesProvider)
        val calculatorTotal = RwamBimmNotProductClassesCalculator(
                fxRateProvider,
                Currency.EUR,
                IsdaConfiguration.INSTANCE)

        val margin = BimmAnalysisUtils.computeMargin(
                combinedRatesProvider,
                normalizer,
                calculatorTotal,
                totalSensitivities,
                totalCurrencyExposure)
        return Triple(margin.first, margin.second, margin.third)
    }

    private fun loadCurveGroup(): CurveGroupDefinition {
        val curveGroups = RatesCalibrationCsvLoader.load(
                ResourceLocator.ofClasspathUrl(javaClass.getResource("/settings/BIMM-groups-EUR.csv")),
                ResourceLocator.ofClasspathUrl(javaClass.getResource("/settings/BIMM-settings-EUR.csv")),
                ResourceLocator.ofClasspathUrl(javaClass.getResource("/settings/BIMM-nodes-EUR.csv")))
        return curveGroups[CurveGroupName.of("BIMM")]!!
    }

    private fun loadMarketData(asOf: LocalDate): MarketData {
        val quotesUrl = javaClass.getResource("/data/BIMM-MARKET-QUOTES-20160606.csv")
        val fxUrl = javaClass.getResource("/data/BIMM-FX-RATES-20160606.csv")

        val quotes = QuotesCsvLoader.load(asOf, ImmutableList.of(ResourceLocator.ofClasspathUrl(quotesUrl)))
        val fxRates = FxRatesCsvLoader.load(asOf, ResourceLocator.ofClasspathUrl(fxUrl))
        return ImmutableMarketData.builder(asOf).addValueMap(quotes).addValueMap(fxRates).build()
    }

    /**
     * Calculates the IM for the entire portfolio excluding each particular trade in order to determine that particular trades contribution to the IM.
     * We assume that eachg trade.info.id field is going to be unique
     */
    override fun calculateSensitivitiesBatch(trades: List<ResolvedSwapTrade>,
                                             pricer: DiscountingSwapProductPricer,
                                             ratesProvider: ImmutableRatesProvider): Map<ResolvedSwapTrade, CurrencyAmount> {
        return trades
                .map {
                    val sensAmountPair = this.sensitivities(trades.omit(it), pricer, ratesProvider)
                    it to CurrencyAmount(sensAmountPair.first, sensAmountPair.second)
                }.toMap()
    }

    override fun calculateMarginBatch(tradeSensitivitiesMap: Map<ResolvedSwapTrade, CurrencyAmount>,
                                      combinedRatesProvider: ImmutableRatesProvider,
                                      fxRateProvider: MarketDataFxRateProvider,
                                      portfolioMargin: InitialMarginTriple): Map<ResolvedSwapTrade, InitialMarginTriple> {
        val normalizer = PortfolioNormalizer(Currency.EUR, combinedRatesProvider) // TODO.. Not just EUR
        val calculatorTotal = RwamBimmNotProductClassesCalculator(fxRateProvider, Currency.EUR, IsdaConfiguration.INSTANCE)
        return tradeSensitivitiesMap.map {
            val t = BimmAnalysisUtils.computeMargin(combinedRatesProvider, normalizer, calculatorTotal, it.value.currencyParameterSensitivities, it.value.multiCurrencyAmount)
            it.key to
                    InitialMarginTriple(portfolioMargin.first - t.first,
                            portfolioMargin.second - t.second,
                            portfolioMargin.third - t.third).toCordaCompatible()
        }.toMap()
    }
}

/**
 * Takes a list of Resolved Swap Trades and returns all but the in the signature.
 */
fun <E> List<E>.omit(ignoree: ResolvedSwapTrade): List<E> {
    return this.filter { it is ResolvedSwapTrade && it != ignoree }
}


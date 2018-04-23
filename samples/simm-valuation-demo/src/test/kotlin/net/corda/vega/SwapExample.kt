/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */
package net.corda.vega

import com.google.common.collect.ImmutableList
import com.opengamma.strata.basics.ReferenceData
import com.opengamma.strata.basics.currency.Currency
import com.opengamma.strata.basics.currency.MultiCurrencyAmount
import com.opengamma.strata.basics.date.Tenor
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
import com.opengamma.strata.pricer.curve.CalibrationMeasures
import com.opengamma.strata.pricer.curve.CurveCalibrator
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer
import com.opengamma.strata.product.common.BuySell
import com.opengamma.strata.product.swap.ResolvedSwapTrade
import com.opengamma.strata.product.swap.SwapTrade
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.vega.analytics.BimmAnalysisUtils
import net.corda.vega.analytics.IsdaConfiguration
import net.corda.vega.analytics.PortfolioNormalizer
import net.corda.vega.analytics.RwamBimmNotProductClassesCalculator
import java.nio.file.Paths
import java.time.LocalDate
import java.util.stream.Collectors.toList
import kotlin.test.assertEquals

/**
 * A example of calculating the margin for a portfolio of two swaps using Strata and SIMM.
 */


fun main(args: Array<String>) {
    val swapExample = SwapExample()
    swapExample.testingEqualitymain(args)
}

class SwapExample {

    val VALUATION_DATE = LocalDate.of(2016, 6, 6)!!

    fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
        val curveGroupDefinition = loadCurveGroup()
        val marketData = loadMarketData()
        val trades = ImmutableList.of(createVanillaFixedVsLibor3mSwap(), createVanillaFixedVsLibor6mSwap())
        val calibrator = CurveCalibrator.of(1e-9, 1e-9, 100, CalibrationMeasures.PAR_SPREAD)
        val ratesProvider = calibrator.calibrate(curveGroupDefinition, marketData, ReferenceData.standard())
        val fxRateProvider = MarketDataFxRateProvider.of(marketData)
        val combinedRatesProvider = ImmutableRatesProvider.combined(fxRateProvider, ratesProvider)

        val resolvedTrades = trades.stream().map({ trade -> trade.resolve(ReferenceData.standard()) }).collect(toList<ResolvedSwapTrade>())
        val pricer = DiscountingSwapProductPricer.DEFAULT

        var totalSensitivities = CurrencyParameterSensitivities.empty()
        var totalCurrencyExposure = MultiCurrencyAmount.empty()

        for (resolvedTrade in resolvedTrades) {
            val swap = resolvedTrade.product

            val pointSensitivities = pricer.presentValueSensitivity(swap, combinedRatesProvider).build()
            val sensitivities = combinedRatesProvider.parameterSensitivity(pointSensitivities)
            val currencyExposure = pricer.currencyExposure(swap, combinedRatesProvider)

            totalSensitivities = totalSensitivities.combinedWith(sensitivities)
            totalCurrencyExposure = totalCurrencyExposure.plus(currencyExposure)
        }
        //val normalizer = PortfolioNormalizer(Currency.EUR, combinedRatesProvider)
        //val calculatorTotal = RwamBimmNotProductClassesCalculator(
        //        fxRateProvider,
        //        Currency.EUR,
        //        IsdaConfiguration.INSTANCE)
//
        //val margin = BimmAnalysisUtils.computeMargin(
        //        combinedRatesProvider,
        //        normalizer,
        //        calculatorTotal,
        //        totalSensitivities,
        //        totalCurrencyExposure)
//
        //println(margin)
    }

    fun testingEqualitymain(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
        val curveGroupDefinition1 = loadCurveGroup()
        val curveGroupDefinition2 = loadCurveGroup()
        assertEquals(curveGroupDefinition2, curveGroupDefinition1)

        val marketData1 = loadMarketData()
        val marketData2 = loadMarketData()
        assertEquals(marketData2, marketData1)

        val trades1 = ImmutableList.of(createVanillaFixedVsLibor3mSwap(), createVanillaFixedVsLibor6mSwap())
        val trades2 = ImmutableList.of(createVanillaFixedVsLibor3mSwap(), createVanillaFixedVsLibor6mSwap())
        assertEquals(trades2, trades1)

        val calibrator1 = CurveCalibrator.of(1e-9, 1e-9, 100, CalibrationMeasures.PAR_SPREAD)
        val calibrator2 = CurveCalibrator.of(1e-9, 1e-9, 100, CalibrationMeasures.PAR_SPREAD)
        assertEquals(calibrator2, calibrator1)

        val ratesProvider1 = calibrator1.calibrate(curveGroupDefinition1, marketData1, ReferenceData.standard())
        val ratesProvider2 = calibrator1.calibrate(curveGroupDefinition1, marketData1, ReferenceData.standard())
        assertEquals(ratesProvider2, ratesProvider1)

        val fxRateProvider1 = MarketDataFxRateProvider.of(marketData1)
        val fxRateProvider2 = MarketDataFxRateProvider.of(marketData2)
        assertEquals(fxRateProvider2, fxRateProvider1)

        val combinedRatesProvider1 = ImmutableRatesProvider.combined(fxRateProvider1, ratesProvider1)
        val combinedRatesProvider2 = ImmutableRatesProvider.combined(fxRateProvider2, ratesProvider2)
        assertEquals(combinedRatesProvider2, combinedRatesProvider1)

        val resolvedTrades = trades1.stream().map({ trade -> trade.resolve(ReferenceData.standard()) }).collect(toList<ResolvedSwapTrade>())
        val pricer = DiscountingSwapProductPricer.DEFAULT

        var totalSensitivities1 = CurrencyParameterSensitivities.empty()
        var totalCurrencyExposure1 = MultiCurrencyAmount.empty()

        for (resolvedTrade in resolvedTrades) {
            val swap = resolvedTrade.product

            val pointSensitivities = pricer.presentValueSensitivity(swap, combinedRatesProvider1).build()
            val sensitivities = combinedRatesProvider1.parameterSensitivity(pointSensitivities)
            val currencyExposure = pricer.currencyExposure(swap, combinedRatesProvider1)

            totalSensitivities1 = totalSensitivities1.combinedWith(sensitivities)
            totalCurrencyExposure1 = totalCurrencyExposure1.plus(currencyExposure)
        }


        var totalSensitivities2 = CurrencyParameterSensitivities.empty()
        var totalCurrencyExposure2 = MultiCurrencyAmount.empty()

        for (resolvedTrade in resolvedTrades) {
            val swap = resolvedTrade.product

            val pointSensitivities = pricer.presentValueSensitivity(swap, combinedRatesProvider2).build()
            val sensitivities = combinedRatesProvider2.parameterSensitivity(pointSensitivities)
            val currencyExposure = pricer.currencyExposure(swap, combinedRatesProvider2)

            totalSensitivities2 = totalSensitivities2.combinedWith(sensitivities)
            totalCurrencyExposure2 = totalCurrencyExposure2.plus(currencyExposure)
        }

        assertEquals(totalSensitivities2, totalSensitivities1)
        assertEquals(totalCurrencyExposure2, totalCurrencyExposure1)

        val totalSensitivities3 = totalSensitivities1.serialize().deserialize()
        assertEquals(totalSensitivities3, totalSensitivities1)
        val totalCurrencyExposure3 = totalCurrencyExposure1.serialize().deserialize()
        assertEquals(totalCurrencyExposure3, totalCurrencyExposure1)

        val normalizer1 = PortfolioNormalizer(Currency.EUR, combinedRatesProvider1)
        val normalizer2 = PortfolioNormalizer(Currency.EUR, combinedRatesProvider2)

        val calculatorTotal1 = RwamBimmNotProductClassesCalculator(
                fxRateProvider1,
                Currency.EUR,
                IsdaConfiguration.INSTANCE)

        val calculatorTotal2 = RwamBimmNotProductClassesCalculator(
                fxRateProvider2,
                Currency.EUR,
                IsdaConfiguration.INSTANCE)

        val margin1 = BimmAnalysisUtils.computeMargin(
                combinedRatesProvider1,
                normalizer1,
                calculatorTotal1,
                totalSensitivities1,
                totalCurrencyExposure1)

        val margin2 = BimmAnalysisUtils.computeMargin(
                combinedRatesProvider2,
                normalizer2,
                calculatorTotal2,
                totalSensitivities2,
                totalCurrencyExposure2)



        println(margin1)
        println(margin2)
    }

    //--------------------------------------------------------------------------------------------------

    /**
     * Load the market quotes and FX rates from data files.
     */
    private fun loadMarketData(): MarketData {
        val dataDir = Paths.get("src/test/resources/data")
        val quotesFile = dataDir.resolve("BIMM-MARKET-QUOTES-20160606.csv")
        val fxFile = dataDir.resolve("BIMM-FX-RATES-20160606.csv")

        val quotes = QuotesCsvLoader.load(VALUATION_DATE, ImmutableList.of(ResourceLocator.ofPath(quotesFile)))
        val fxRates = FxRatesCsvLoader.load(VALUATION_DATE, ResourceLocator.ofPath(fxFile))
        return ImmutableMarketData.builder(VALUATION_DATE).addValueMap(quotes).addValueMap(fxRates).build()
    }

    /**
     * Loads the curve group definition from data files.

     * A curve group maps from curve name to index for forward curves and curve name to currency for discount curves.
     */
    private fun loadCurveGroup(): CurveGroupDefinition {
        val settingsDir = Paths.get("src/test/resources/settings")
        val curveGroups = RatesCalibrationCsvLoader.load(
                ResourceLocator.ofPath(settingsDir.resolve("BIMM-groups-EUR.csv")),
                ResourceLocator.ofPath(settingsDir.resolve("BIMM-settings-EUR.csv")),
                ResourceLocator.ofPath(settingsDir.resolve("BIMM-nodes-EUR.csv")))
        return curveGroups[CurveGroupName.of("BIMM")]!!
    }

    //--------------------------------------------------------------------------------------------------

    private fun createVanillaFixedVsLibor3mSwap(): SwapTrade {
        return FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M.createTrade(
                VALUATION_DATE,
                Tenor.TENOR_4Y,
                BuySell.BUY,
                200000000.0,
                0.015,
                ReferenceData.standard())
    }

    private fun createVanillaFixedVsLibor6mSwap(): SwapTrade {
        return FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M.createTrade(
                VALUATION_DATE,
                Tenor.TENOR_10Y,
                BuySell.SELL,
                100000000.0,
                0.013,
                ReferenceData.standard())
    }
}

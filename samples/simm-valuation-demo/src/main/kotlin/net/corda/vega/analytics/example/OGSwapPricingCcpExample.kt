/**
 * Copyright (C) 2016 - present by OpenGamma Inc. and the OpenGamma group of companies

 * Please see distribution for license.
 */
package net.corda.vega.analytics.example

import com.google.common.collect.ImmutableList
import com.opengamma.strata.basics.ReferenceData
import com.opengamma.strata.basics.StandardId
import com.opengamma.strata.calc.CalculationRules
import com.opengamma.strata.calc.CalculationRunner
import com.opengamma.strata.calc.Column
import com.opengamma.strata.calc.marketdata.MarketDataConfig
import com.opengamma.strata.calc.marketdata.MarketDataRequirements
import com.opengamma.strata.collect.io.ResourceLocator
import com.opengamma.strata.data.ImmutableMarketData
import com.opengamma.strata.examples.marketdata.ExampleData
import com.opengamma.strata.loader.csv.FixingSeriesCsvLoader
import com.opengamma.strata.loader.csv.QuotesCsvLoader
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader
import com.opengamma.strata.market.curve.CurveGroupName
import com.opengamma.strata.measure.Measures
import com.opengamma.strata.measure.StandardComponents
import com.opengamma.strata.measure.StandardComponents.marketDataFactory
import com.opengamma.strata.measure.calc.TradeCounterpartyCalculationParameter
import com.opengamma.strata.measure.rate.RatesMarketDataLookup
import com.opengamma.strata.product.Trade
import com.opengamma.strata.product.TradeAttributeType
import com.opengamma.strata.product.TradeInfo
import com.opengamma.strata.product.common.BuySell
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions
import com.opengamma.strata.report.ReportCalculationResults
import com.opengamma.strata.report.trade.TradeReport
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.toPath
import java.nio.file.Path
import java.time.LocalDate

/**
 * Example to illustrate using the engine to price a swap.
 *
 *
 * This makes use of the example engine and the example market data environment.
 */
fun main(args: Array<String>) {
    val swapPricingCcpExample = SwapPricingCcpExample()
    swapPricingCcpExample.main(args)
}

class SwapPricingCcpExample {

    /**
     * The valuation date.
     */
    private val VAL_DATE = LocalDate.of(2015, 7, 21)
    /**
     * The curve group name.
     */
    private val CURVE_GROUP_NAME_CCP1 = CurveGroupName.of("USD-DSCON-LIBOR3M")
    private val CURVE_GROUP_NAME_CCP2 = CurveGroupName.of("USD-DSCON-LIBOR3M-CCP2")
    /**
     * The location of the data files.
     */
    private val resourcesUri = run {
        // Find src/main/resources by walking up the directory tree starting at a classpath root:
        var module = javaClass.getResource("/").toPath()
        val relative = "src" / "main" / "resources"
        var path: Path
        while (true) {
            path = module.resolve(relative)
            path.exists() && break
            module = module.parent
        }
        path.toUri()
    }

    private fun resourceLocator(uri: String) = ResourceLocator.ofUrl(resourcesUri.resolve(uri).toURL())
    /**
     * The location of the curve calibration groups file for CCP1 and CCP2.
     */
    private val GROUPS_RESOURCE_CCP1 = resourceLocator("example-calibration/curves/groups.csv")
    private val GROUPS_RESOURCE_CCP2 = resourceLocator("example-calibration/curves/groups-ccp2.csv")
    /**
     * The location of the curve calibration settings file for CCP1 and CCP2.
     */
    private val SETTINGS_RESOURCE_CCP1 = resourceLocator("example-calibration/curves/settings.csv")
    private val SETTINGS_RESOURCE_CCP2 = resourceLocator("example-calibration/curves/settings-ccp2.csv")
    /**
     * The location of the curve calibration nodes file for CCP1 and CCP2.
     */
    private val CALIBRATION_RESOURCE_CCP1 = resourceLocator("example-calibration/curves/calibrations.csv")
    private val CALIBRATION_RESOURCE_CCP2 = resourceLocator("example-calibration/curves/calibrations-ccp2.csv")
    /**
     * The location of the market quotes file for CCP1 and CCP2.
     */
    private val QUOTES_RESOURCE_CCP1 = resourceLocator("example-calibration/quotes/quotes.csv")
    private val QUOTES_RESOURCE_CCP2 = resourceLocator("example-calibration/quotes/quotes-ccp2.csv")
    /**
     * The location of the historical fixing file.
     */
    private val FIXINGS_RESOURCE = resourceLocator("example-marketdata/historical-fixings/usd-libor-3m.csv")

    /**
     * The first counterparty.
     */
    private val CCP1_ID = StandardId.of("example", "CCP-1")
    /**
     * The second counterparty.
     */
    private val CCP2_ID = StandardId.of("example", "CCP-2")

    /**
     * Runs the example, pricing the instruments, producing the output as an ASCII table.

     * @param args  ignored
     */
    fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
        // setup calculation runner component, which needs life-cycle management
        // a typical application might use dependency injection to obtain the instance
        val runner = CalculationRunner.ofMultiThreaded()
        calculate(runner)
//        CalculationRunner.ofMultiThreaded().use { runner -> calculate(runner) }
    }

    // obtains the data and calculates the grid of results
    private fun calculate(runner: CalculationRunner) {
        // the trades that will have measures calculated
        val trades = createSwapTrades()

        // the columns, specifying the measures to be calculated
        val columns = ImmutableList.of(
                Column.of(Measures.PRESENT_VALUE),
                Column.of(Measures.PAR_RATE),
                Column.of(Measures.PV01_MARKET_QUOTE_BUCKETED),
                Column.of(Measures.PV01_CALIBRATED_BUCKETED))

        // load quotes
        val quotesCcp1 = QuotesCsvLoader.load(VAL_DATE, QUOTES_RESOURCE_CCP1)
        val quotesCcp2 = QuotesCsvLoader.load(VAL_DATE, QUOTES_RESOURCE_CCP2)

        // load fixings
        val fixings = FixingSeriesCsvLoader.load(FIXINGS_RESOURCE)

        // create the market data
        val marketData = ImmutableMarketData.builder(VAL_DATE).addValueMap(quotesCcp1).addValueMap(quotesCcp2).addTimeSeriesMap(fixings).build()

        // the reference data, such as holidays and securities
        val refData = ReferenceData.standard()

        // load the curve definition
        val defnsCcp1 = RatesCalibrationCsvLoader.load(GROUPS_RESOURCE_CCP1, SETTINGS_RESOURCE_CCP1, CALIBRATION_RESOURCE_CCP1)
        val defnsCcp2 = RatesCalibrationCsvLoader.load(GROUPS_RESOURCE_CCP2, SETTINGS_RESOURCE_CCP2, CALIBRATION_RESOURCE_CCP2)
        val curveGroupDefinitionCcp1 = defnsCcp1[CURVE_GROUP_NAME_CCP1]!!.filtered(VAL_DATE, refData)
        val curveGroupDefinitionCcp2 = defnsCcp2[CURVE_GROUP_NAME_CCP2]!!.filtered(VAL_DATE, refData)

        // the configuration that defines how to create the curves when a curve group is requested
        val marketDataConfig = MarketDataConfig.builder().add(CURVE_GROUP_NAME_CCP1, curveGroupDefinitionCcp1).add(CURVE_GROUP_NAME_CCP2, curveGroupDefinitionCcp2).build()

        // the complete set of rules for calculating measures
        val functions = StandardComponents.calculationFunctions()
        val ratesLookupCcp1 = RatesMarketDataLookup.of(curveGroupDefinitionCcp1)
        val ratesLookupCcp2 = RatesMarketDataLookup.of(curveGroupDefinitionCcp2)
        // choose RatesMarketDataLookup instance based on counterparty

        val perCounterparty = TradeCounterpartyCalculationParameter.of(
                mapOf(CCP1_ID to ratesLookupCcp1, CCP2_ID to ratesLookupCcp2), ratesLookupCcp1)
        val rules = CalculationRules.of(functions, perCounterparty)

        // calibrate the curves and calculate the results
        val reqs = MarketDataRequirements.of(rules, trades, columns, refData)
        val calibratedMarketData = marketDataFactory().create(reqs, marketDataConfig, marketData, refData)
        val results = runner.calculate(rules, trades, columns, calibratedMarketData, refData)

        // use the report runner to transform the engine results into a trade report
        val calculationResults = ReportCalculationResults.of(VAL_DATE, trades, columns, results, functions, refData)
        val reportTemplate = ExampleData.loadTradeReportTemplate("swap-report-template2")
        val tradeReport = TradeReport.of(calculationResults, reportTemplate)
        tradeReport.writeAsciiTable(System.out)
    }

    //-----------------------------------------------------------------------  
    // create swap trades
    private fun createSwapTrades(): List<Trade> {
        return ImmutableList.of(createVanillaFixedVsLibor3mSwap(CCP1_ID), createVanillaFixedVsLibor3mSwap(CCP2_ID))
    }

    //-----------------------------------------------------------------------  
    // create a vanilla fixed vs libor 3m swap
    private fun createVanillaFixedVsLibor3mSwap(ctptyId: StandardId): Trade {
        val tradeInfo = TradeInfo.builder().id(StandardId.of("example", "1")).addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 3m").counterparty(ctptyId).settlementDate(LocalDate.of(2014, 9, 12)).build()
        return FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M.toTrade(
                tradeInfo,
                LocalDate.of(2014, 9, 12), // the start date
                LocalDate.of(2021, 9, 12), // the end date
                BuySell.BUY, // indicates whether this trade is a buy or sell
                100000000.0, // the notional amount  
                0.015)                    // the fixed interest rate
    }

}

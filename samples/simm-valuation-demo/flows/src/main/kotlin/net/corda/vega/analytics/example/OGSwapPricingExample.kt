package net.corda.vega.analytics.example

import com.google.common.collect.ImmutableList
import com.opengamma.strata.basics.ReferenceData
import com.opengamma.strata.basics.StandardId
import com.opengamma.strata.basics.currency.Currency
import com.opengamma.strata.basics.date.BusinessDayAdjustment
import com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING
import com.opengamma.strata.basics.date.DayCounts
import com.opengamma.strata.basics.date.DaysAdjustment
import com.opengamma.strata.basics.date.HolidayCalendarIds
import com.opengamma.strata.basics.index.IborIndices
import com.opengamma.strata.basics.index.OvernightIndices
import com.opengamma.strata.basics.schedule.Frequency
import com.opengamma.strata.basics.schedule.PeriodicSchedule
import com.opengamma.strata.basics.schedule.StubConvention
import com.opengamma.strata.basics.value.ValueSchedule
import com.opengamma.strata.calc.CalculationRules
import com.opengamma.strata.calc.CalculationRunner
import com.opengamma.strata.calc.Column
import com.opengamma.strata.examples.marketdata.ExampleData
import com.opengamma.strata.examples.marketdata.ExampleMarketData
import com.opengamma.strata.measure.Measures
import com.opengamma.strata.measure.StandardComponents
import com.opengamma.strata.product.Trade
import com.opengamma.strata.product.TradeAttributeType
import com.opengamma.strata.product.TradeInfo
import com.opengamma.strata.product.common.BuySell
import com.opengamma.strata.product.common.PayReceive
import com.opengamma.strata.product.swap.*
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions
import com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions
import com.opengamma.strata.report.ReportCalculationResults
import com.opengamma.strata.report.trade.TradeReport
import java.time.LocalDate

/**
 * Example to illustrate using the calculation API to price a swap.
 *
 *
 * This makes use of the example market data environment.
 */


fun main(args: Array<String>) {
    val swapPricingExample = SwapPricingExample()
    swapPricingExample.main()
}

/**
 * Another way of creating test trades
 */
fun testOGTradeContract() {
    val swapPricingExample = SwapPricingExample()
    swapPricingExample.createSwapTrades()
}

class SwapPricingExample {
    /**
     * Runs the example, pricing the instruments, producing the output as an ASCII table.
     */
    fun main() {
        // setup calculation runner component, which needs life-cycle management
        // a typical application might use dependency injection to obtain the instance
        val runner = CalculationRunner.ofMultiThreaded()
        calculate(runner)
    }

    // obtains the data and calculates the grid of results
    private fun calculate(runner: CalculationRunner) {
        // the trades that will have measures calculated
        val trades = createSwapTrades()

        // the columns, specifying the measures to be calculated
        val columns = ImmutableList.of(
                Column.of(Measures.LEG_INITIAL_NOTIONAL),
                Column.of(Measures.PRESENT_VALUE),
                Column.of(Measures.LEG_PRESENT_VALUE),
                Column.of(Measures.PV01_CALIBRATED_SUM),
                Column.of(Measures.PAR_RATE),
                Column.of(Measures.ACCRUED_INTEREST),
                Column.of(Measures.PV01_CALIBRATED_BUCKETED),
                Column.of(Measures.PV01_MARKET_QUOTE_BUCKETED),
                Column.of(Measures.CURRENCY_EXPOSURE)
                //     Column.of()
                //        Column.of(AdvancedMeasures.PV01_SEMI_PARALLEL_GAMMA_BUCKETED)
        )

        // use the built-in example market data
        val valuationDate = LocalDate.of(2014, 1, 22)
        val marketDataBuilder = ExampleMarketData.builder()
        val marketData = marketDataBuilder.buildSnapshot(valuationDate)

        // the complete set of rules for calculating measures
        val functions = StandardComponents.calculationFunctions()
        val rules = CalculationRules.of(functions, marketDataBuilder.ratesLookup(valuationDate))

        // the reference data, such as holidays and securities
        val refData = ReferenceData.standard()

        // calculate the results
        val results = runner.calculate(rules, trades, columns, marketData, refData)

        // use the report runner to transform the engine results into a trade report
        val calculationResults = ReportCalculationResults.of(valuationDate, trades, columns, results, functions, refData)

        val reportTemplate = ExampleData.loadTradeReportTemplate("swap-report-template")
        val tradeReport = TradeReport.of(calculationResults, reportTemplate)
        tradeReport.writeAsciiTable(System.out)
    }

    //-----------------------------------------------------------------------
    // create swap trades
    fun createSwapTrades(): List<Trade> {
        return ImmutableList.of(
                createVanillaFixedVsLibor3mSwap(),
                createBasisLibor3mVsLibor6mWithSpreadSwap(),
                createOvernightAveragedWithSpreadVsLibor3mSwap(),
                createFixedVsLibor3mWithFixingSwap(),
                createFixedVsOvernightWithFixingSwap(),
                createStub3mFixedVsLibor3mSwap(),
                createStub1mFixedVsLibor3mSwap(),
                createInterpolatedStub3mFixedVsLibor6mSwap(),
                createInterpolatedStub4mFixedVsLibor6mSwap(),
                createZeroCouponFixedVsLibor3mSwap(),
                createCompoundingFixedVsFedFundsSwap(),
                createCompoundingFedFundsVsLibor3mSwap(),
                createCompoundingLibor6mVsLibor3mSwap(),
                createXCcyGbpLibor3mVsUsdLibor3mSwap(),
                createXCcyUsdFixedVsGbpLibor3mSwap(),
                createNotionalExchangeSwap())
    }

    //-----------------------------------------------------------------------
    // create a vanilla fixed vs libor 3m swap
    private fun createVanillaFixedVsLibor3mSwap(): Trade {
        val tradeInfo = TradeInfo.builder()
                .id(StandardId.of("example", "1"))
                .addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 3m")
                .counterparty(StandardId.of("example", "A"))
                .settlementDate(LocalDate.of(2014, 9, 12))
                .build()
        return FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M.toTrade(
                tradeInfo,
                LocalDate.of(2014, 9, 12), // the start date
                LocalDate.of(2021, 9, 12), // the end date
                BuySell.BUY, // indicates wheter this trade is a buy or sell
                100000000.0, // the notional amount
                0.015)                    // the fixed interest rate
    }

    // create a libor 3m vs libor 6m basis swap with spread
    private fun createBasisLibor3mVsLibor6mWithSpreadSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder()
                .payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder()
                .startDate(LocalDate.of(2014, 8, 27))
                .endDate(LocalDate.of(2024, 8, 27))
                .frequency(Frequency.P6M)
                .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY))
                .build())
                .paymentSchedule(
                        PaymentSchedule.builder()
                                .paymentFrequency(Frequency.P6M)
                                .paymentDateOffset(DaysAdjustment.NONE).build())
                .notionalSchedule(notional)
                .calculation(IborRateCalculation.of(IborIndices.USD_LIBOR_6M)).build()

        val receiveLeg =
                RateCalculationSwapLeg.builder().
                        payReceive(PayReceive.RECEIVE)
                        .accrualSchedule(PeriodicSchedule
                                .builder()
                                .startDate(LocalDate.of(2014, 8, 27))
                                .endDate(LocalDate.of(2024, 8, 27))
                                .frequency(Frequency.P3M)
                                .businessDayAdjustment(BusinessDayAdjustment.
                                        of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).
                                build()).
                        paymentSchedule(PaymentSchedule
                                .builder()
                                .paymentFrequency(Frequency.P3M)
                                .paymentDateOffset(DaysAdjustment.NONE)
                                .build())
                        .notionalSchedule(notional)
                        .calculation(IborRateCalculation
                                .builder()
                                .index(IborIndices.USD_LIBOR_3M)
                                .spread(ValueSchedule.of(0.001))
                                .build())
                        .build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "2")).addAttribute(TradeAttributeType.DESCRIPTION, "Libor 3m + spread vs Libor 6m").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 9, 12)).build()).build()
    }

    // Create an overnight averaged vs libor 3m swap with spread
    private fun createOvernightAveragedWithSpreadVsLibor3mSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2020, 9, 12)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P3M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(IborRateCalculation.of(IborIndices.USD_LIBOR_3M)).build()

        val receiveLeg = RateCalculationSwapLeg.builder()
                .payReceive(PayReceive.RECEIVE)
                .accrualSchedule(PeriodicSchedule.builder()
                        .startDate(LocalDate.of(2014, 9, 12))
                        .endDate(LocalDate.of(2020, 9, 12))
                        .frequency(Frequency.P3M)
                        .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build())
                .paymentSchedule(PaymentSchedule.builder()
                        .paymentFrequency(Frequency.P3M)
                        .paymentDateOffset(DaysAdjustment.NONE).build())
                .notionalSchedule(notional).calculation(OvernightRateCalculation.builder()
                .dayCount(DayCounts.ACT_360).index(OvernightIndices.USD_FED_FUND)
                .accrualMethod(OvernightAccrualMethod.AVERAGED)
                .spread(ValueSchedule.of(0.0025)).build())
                .build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg))
                .info(TradeInfo.builder()
                        .id(StandardId.of("example", "3"))
                        .addAttribute(TradeAttributeType.DESCRIPTION, "Fed Funds averaged + spread vs Libor 3m")
                        .counterparty(StandardId.of("example", "A"))
                        .settlementDate(LocalDate.of(2014, 9, 12)).build()).build()
    }

    // Create a vanilla fixed vs libor 3m swap with fixing
    private fun createFixedVsLibor3mWithFixingSwap(): Trade {
        val tradeInfo = TradeInfo.builder()
                .id(StandardId.of("example", "4"))
                .addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs libor 3m (with fixing)")
                .counterparty(StandardId.of("example", "A"))
                .settlementDate(LocalDate.of(2013, 9, 12)).build()
        return FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M.toTrade(
                tradeInfo,
                LocalDate.of(2013, 9, 12), // the start date
                LocalDate.of(2020, 9, 12), // the end date
                BuySell.BUY, // indicates wheter this trade is a buy or sell
                100000000.0, // the notional amount
                0.015)                    // the fixed interest rate
    }

    // Create a fixed vs overnight swap with fixing
    private fun createFixedVsOvernightWithFixingSwap(): Trade {
        val tradeInfo = TradeInfo.builder().id(StandardId.of("example", "5"))
                .addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs ON (with fixing)")
                .counterparty(StandardId.of("example", "A"))
                .settlementDate(LocalDate.of(2014, 1, 17)).build()
        return FixedOvernightSwapConventions.USD_FIXED_TERM_FED_FUND_OIS.toTrade(
                tradeInfo,
                LocalDate.of(2014, 1, 17), // the start date
                LocalDate.of(2014, 3, 17), // the end date
                BuySell.BUY, // indicates wheter this trade is a buy or sell
                100000000.0, // the notional amount
                0.00123)                  // the fixed interest rate
    }

    // Create a fixed vs libor 3m swap
    private fun createStub3mFixedVsLibor3mSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder()
                .payReceive(PayReceive.PAY)
                .accrualSchedule(PeriodicSchedule.builder()
                        .startDate(LocalDate.of(2014, 9, 12))
                        .endDate(LocalDate.of(2016, 6, 12))
                        .frequency(Frequency.P3M)
                        .businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build())
                .paymentSchedule(PaymentSchedule.builder()
                        .paymentFrequency(Frequency.P3M)
                        .paymentDateOffset(DaysAdjustment.NONE).build())
                .notionalSchedule(notional)
                .calculation(IborRateCalculation.of(IborIndices.USD_LIBOR_3M)).build()

        val receiveLeg = RateCalculationSwapLeg.builder()
                .payReceive(PayReceive.RECEIVE)
                .accrualSchedule(PeriodicSchedule.builder()
                        .startDate(LocalDate.of(2014, 9, 12))
                        .endDate(LocalDate.of(2016, 6, 12))
                        .stubConvention(StubConvention.SHORT_INITIAL)
                        .frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build())
                .paymentSchedule(PaymentSchedule.builder()
                        .paymentFrequency(Frequency.P6M)
                        .paymentDateOffset(DaysAdjustment.NONE).build())
                .notionalSchedule(notional)
                .calculation(FixedRateCalculation.of(0.01, DayCounts.THIRTY_U_360)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder()
                .id(StandardId.of("example", "6"))
                .addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 3m (3m short initial stub)")
                .counterparty(StandardId.of("example", "A"))
                .settlementDate(LocalDate.of(2014, 9, 12)).build()).build()
    }

    // Create a fixed vs libor 3m swap
    private fun createStub1mFixedVsLibor3mSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2016, 7, 12)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).stubConvention(StubConvention.SHORT_INITIAL).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P3M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(IborRateCalculation.of(IborIndices.USD_LIBOR_3M)).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2016, 7, 12)).stubConvention(StubConvention.SHORT_INITIAL).frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(FixedRateCalculation.of(0.01, DayCounts.THIRTY_U_360)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "7")).addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 3m (1m short initial stub)").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 9, 12)).build()).build()
    }

    // Create a fixed vs libor 6m swap
    private fun createInterpolatedStub3mFixedVsLibor6mSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2016, 6, 12)).frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).stubConvention(StubConvention.SHORT_INITIAL).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(IborRateCalculation.builder().index(IborIndices.USD_LIBOR_6M).initialStub(IborRateStubCalculation.ofIborInterpolatedRate(IborIndices.USD_LIBOR_3M, IborIndices.USD_LIBOR_6M)).build()).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2016, 6, 12)).stubConvention(StubConvention.SHORT_INITIAL).frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(FixedRateCalculation.of(0.01, DayCounts.THIRTY_U_360)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "8")).addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 6m (interpolated 3m short initial stub)").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 9, 12)).build()).build()
    }

    // Create a fixed vs libor 6m swap
    private fun createInterpolatedStub4mFixedVsLibor6mSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2016, 7, 12)).frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).stubConvention(StubConvention.SHORT_INITIAL).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(IborRateCalculation.builder().index(IborIndices.USD_LIBOR_6M).initialStub(IborRateStubCalculation.ofIborInterpolatedRate(IborIndices.USD_LIBOR_3M, IborIndices.USD_LIBOR_6M)).build()).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2016, 7, 12)).stubConvention(StubConvention.SHORT_INITIAL).frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(FixedRateCalculation.of(0.01, DayCounts.THIRTY_U_360)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "9")).addAttribute(TradeAttributeType.DESCRIPTION, "Fixed vs Libor 6m (interpolated 4m short initial stub)").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 9, 12)).build()).build()
    }

    // Create a zero-coupon fixed vs libor 3m swap
    private fun createZeroCouponFixedVsLibor3mSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2021, 9, 12)).frequency(Frequency.P12M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.TERM).paymentDateOffset(DaysAdjustment.NONE).compoundingMethod(CompoundingMethod.STRAIGHT).build()).notionalSchedule(notional).calculation(FixedRateCalculation.of(0.015, DayCounts.THIRTY_U_360)).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2021, 9, 12)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.TERM).paymentDateOffset(DaysAdjustment.NONE).compoundingMethod(CompoundingMethod.STRAIGHT).build()).notionalSchedule(notional).calculation(IborRateCalculation.of(IborIndices.USD_LIBOR_3M)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "10")).addAttribute(TradeAttributeType.DESCRIPTION, "Zero-coupon fixed vs libor 3m").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 9, 12)).build()).build()
    }

    // Create a compounding fixed vs fed funds swap
    private fun createCompoundingFixedVsFedFundsSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 2, 5)).endDate(LocalDate.of(2014, 4, 7)).frequency(Frequency.TERM).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.TERM).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(FixedRateCalculation.of(0.00123, DayCounts.ACT_360)).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 2, 5)).endDate(LocalDate.of(2014, 4, 7)).frequency(Frequency.TERM).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).stubConvention(StubConvention.SHORT_INITIAL).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.TERM).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(OvernightRateCalculation.of(OvernightIndices.USD_FED_FUND)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "11")).addAttribute(TradeAttributeType.DESCRIPTION, "Compounding fixed vs fed funds").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 2, 5)).build()).build()
    }

    // Create a compounding fed funds vs libor 3m swap
    private fun createCompoundingFedFundsVsLibor3mSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2020, 9, 12)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P3M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(IborRateCalculation.of(IborIndices.USD_LIBOR_3M)).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 9, 12)).endDate(LocalDate.of(2020, 9, 12)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P3M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(OvernightRateCalculation.builder().index(OvernightIndices.USD_FED_FUND).accrualMethod(OvernightAccrualMethod.AVERAGED).build()).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "12")).addAttribute(TradeAttributeType.DESCRIPTION, "Compounding fed funds vs libor 3m").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 9, 12)).build()).build()
    }

    // Create a compounding libor 6m vs libor 3m swap
    private fun createCompoundingLibor6mVsLibor3mSwap(): Trade {
        val notional = NotionalSchedule.of(Currency.USD, 100000000.0)

        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 8, 27)).endDate(LocalDate.of(2024, 8, 27)).frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(notional).calculation(IborRateCalculation.of(IborIndices.USD_LIBOR_6M)).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 8, 27)).endDate(LocalDate.of(2024, 8, 27)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).compoundingMethod(CompoundingMethod.STRAIGHT).build()).notionalSchedule(notional).calculation(IborRateCalculation.of(IborIndices.USD_LIBOR_3M)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "13")).addAttribute(TradeAttributeType.DESCRIPTION, "Compounding libor 6m vs libor 3m").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 8, 27)).build()).build()
    }

    // create a cross-currency GBP libor 3m vs USD libor 3m swap with spread
    private fun createXCcyGbpLibor3mVsUsdLibor3mSwap(): Trade {
        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 1, 24)).endDate(LocalDate.of(2021, 1, 24)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.GBLO)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P3M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(NotionalSchedule.of(Currency.GBP, 61600000.0)).calculation(IborRateCalculation.of(IborIndices.GBP_LIBOR_3M)).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 1, 24)).endDate(LocalDate.of(2021, 1, 24)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.USNY)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P3M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(NotionalSchedule.of(Currency.USD, 100000000.0)).calculation(IborRateCalculation.builder().index(IborIndices.USD_LIBOR_3M).spread(ValueSchedule.of(0.0091)).build()).build()

        return SwapTrade.builder().product(Swap.of(receiveLeg, payLeg)).info(TradeInfo.builder().id(StandardId.of("example", "14")).addAttribute(TradeAttributeType.DESCRIPTION, "GBP Libor 3m vs USD Libor 3m").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 1, 24)).build()).build()
    }

    // create a cross-currency USD fixed vs GBP libor 3m swap
    private fun createXCcyUsdFixedVsGbpLibor3mSwap(): SwapTrade {
        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 1, 24)).endDate(LocalDate.of(2021, 1, 24)).frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.GBLO)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(NotionalSchedule.of(Currency.USD, 100000000.0)).calculation(FixedRateCalculation.of(0.03, DayCounts.THIRTY_U_360)).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 1, 24)).endDate(LocalDate.of(2021, 1, 24)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.GBLO)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P3M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(NotionalSchedule.of(Currency.GBP, 61600000.0)).calculation(IborRateCalculation.of(IborIndices.GBP_LIBOR_3M)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "15")).addAttribute(TradeAttributeType.DESCRIPTION, "USD fixed vs GBP Libor 3m").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 1, 24)).build()).build()
    }

    // create a cross-currency USD fixed vs GBP libor 3m swap with initial and final notional exchange
    private fun createNotionalExchangeSwap(): SwapTrade {
        val payLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.PAY).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 1, 24)).endDate(LocalDate.of(2021, 1, 24)).frequency(Frequency.P6M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.GBLO)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P6M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(NotionalSchedule.builder().currency(Currency.USD).amount(ValueSchedule.of(100000000.0)).initialExchange(true).finalExchange(true).build()).calculation(FixedRateCalculation.of(0.03, DayCounts.THIRTY_U_360)).build()

        val receiveLeg = RateCalculationSwapLeg.builder().payReceive(PayReceive.RECEIVE).accrualSchedule(PeriodicSchedule.builder().startDate(LocalDate.of(2014, 1, 24)).endDate(LocalDate.of(2021, 1, 24)).frequency(Frequency.P3M).businessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, HolidayCalendarIds.GBLO)).build()).paymentSchedule(PaymentSchedule.builder().paymentFrequency(Frequency.P3M).paymentDateOffset(DaysAdjustment.NONE).build()).notionalSchedule(NotionalSchedule.builder().currency(Currency.GBP).amount(ValueSchedule.of(61600000.0)).initialExchange(true).finalExchange(true).build()).calculation(IborRateCalculation.of(IborIndices.GBP_LIBOR_3M)).build()

        return SwapTrade.builder().product(Swap.of(payLeg, receiveLeg)).info(TradeInfo.builder().id(StandardId.of("example", "16")).addAttribute(TradeAttributeType.DESCRIPTION, "USD fixed vs GBP Libor 3m (notional exchange)").counterparty(StandardId.of("example", "A")).settlementDate(LocalDate.of(2014, 1, 24)).build()).build()
    }

}

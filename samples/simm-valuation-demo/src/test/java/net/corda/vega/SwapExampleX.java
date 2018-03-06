/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */
package net.corda.vega;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.data.FxRateId;
import com.opengamma.strata.data.ImmutableMarketData;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.data.MarketDataFxRateProvider;
import com.opengamma.strata.loader.csv.FxRatesCsvLoader;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.curve.CalibrationMeasures;
import com.opengamma.strata.pricer.curve.CurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * A example of calculating the margin for a portfolio of two swaps using Strata and SIMM.
 */
public class SwapExampleX {

    public static final LocalDate VALUATION_DATE = LocalDate.of(2016, 6, 6);

    public static void main(String[] args) {
        CurveGroupDefinition curveGroupDefinition = loadCurveGroup();
        MarketData marketData = loadMarketData();
        List<SwapTrade> trades = ImmutableList.of(createVanillaFixedVsLibor3mSwap(), createVanillaFixedVsLibor6mSwap());
        CurveCalibrator calibrator = CurveCalibrator.of(1e-9, 1e-9, 100, CalibrationMeasures.PAR_SPREAD);
        ImmutableRatesProvider ratesProvider = calibrator.calibrate(curveGroupDefinition, marketData, ReferenceData.standard());
        MarketDataFxRateProvider fxRateProvider = MarketDataFxRateProvider.of(marketData);
        ImmutableRatesProvider combinedRatesProvider = ImmutableRatesProvider.combined(fxRateProvider, ratesProvider);

        List<ResolvedSwapTrade> resolvedTrades = trades.stream().map(trade -> trade.resolve(ReferenceData.standard())).collect(toList());
        DiscountingSwapProductPricer pricer = DiscountingSwapProductPricer.DEFAULT;

        CurrencyParameterSensitivities totalSensitivities = CurrencyParameterSensitivities.empty();
        MultiCurrencyAmount totalCurrencyExposure = MultiCurrencyAmount.empty();

        for (ResolvedSwapTrade resolvedTrade : resolvedTrades) {
            ResolvedSwap swap = resolvedTrade.getProduct();

            PointSensitivities pointSensitivities = pricer.presentValueSensitivity(swap, combinedRatesProvider).build();
            CurrencyParameterSensitivities sensitivities = combinedRatesProvider.parameterSensitivity(pointSensitivities);
            MultiCurrencyAmount currencyExposure = pricer.currencyExposure(swap, combinedRatesProvider);

            totalSensitivities = totalSensitivities.combinedWith(sensitivities);
            totalCurrencyExposure = totalCurrencyExposure.plus(currencyExposure);
        }
        //PortfolioNormalizer normalizer = new PortfolioNormalizer(Currency.EUR, combinedRatesProvider);
        //RwamBimmNotProductClassesCalculator calculatorTotal = new RwamBimmNotProductClassesCalculator(
        //    fxRateProvider,
        //    Currency.EUR,
        //    IsdaConfiguration.INSTANCE);
//
        //Triple<Double, Double, Double> margin = BimmAnalysisUtils.computeMargin(
        //    combinedRatesProvider,
        //    normalizer,
        //    calculatorTotal,
        //    totalSensitivities,
        //    totalCurrencyExposure);
//
        //System.out.println(margin);
    }

    //--------------------------------------------------------------------------------------------------

    /**
     * Load the market quotes and FX rates from data files.
     */
    private static MarketData loadMarketData() {
        Path dataDir = Paths.get("src/test/resources/data");
        Path quotesFile = dataDir.resolve("BIMM-MARKET-QUOTES-20160606.csv");
        Path fxFile = dataDir.resolve("BIMM-FX-RATES-20160606.csv");

        Map<QuoteId, Double> quotes = QuotesCsvLoader.load(VALUATION_DATE, ImmutableList.of(ResourceLocator.ofPath(quotesFile)));
        Map<FxRateId, FxRate> fxRates = FxRatesCsvLoader.load(VALUATION_DATE, ResourceLocator.ofPath(fxFile));
        return ImmutableMarketData.builder(VALUATION_DATE).addValueMap(quotes).addValueMap(fxRates).build();
    }

    /**
     * Loads the curve group definition from data files.
     * <p>
     * A curve group maps from curve name to index for forward curves and curve name to currency for discount curves.
     */
    private static CurveGroupDefinition loadCurveGroup() {
        Path settingsDir = Paths.get("src/test/resources/settings");
        Map<CurveGroupName, CurveGroupDefinition> curveGroups = RatesCalibrationCsvLoader.load(
                ResourceLocator.ofPath(settingsDir.resolve("BIMM-groups-EUR.csv")),
                ResourceLocator.ofPath(settingsDir.resolve("BIMM-settings-EUR.csv")),
                ResourceLocator.ofPath(settingsDir.resolve("BIMM-nodes-EUR.csv")));
        return curveGroups.get(CurveGroupName.of("BIMM"));
    }

    //--------------------------------------------------------------------------------------------------

    private static SwapTrade createVanillaFixedVsLibor3mSwap() {
        return FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M.createTrade(
                VALUATION_DATE,
                Tenor.TENOR_4Y,
                BuySell.BUY,
                200_000_000,
                0.015,
                ReferenceData.standard());
    }

    private static SwapTrade createVanillaFixedVsLibor6mSwap() {
        return FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M.createTrade(
                VALUATION_DATE,
                Tenor.TENOR_10Y,
                BuySell.SELL,
                100_000_000,
                0.013,
                ReferenceData.standard());
    }
}

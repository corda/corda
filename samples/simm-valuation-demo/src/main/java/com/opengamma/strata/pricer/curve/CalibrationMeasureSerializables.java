/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * <p>
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.curve;

import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.deposit.DiscountingIborFixingDepositProductPricer;
import com.opengamma.strata.pricer.deposit.DiscountingTermDepositProductPricer;
import com.opengamma.strata.pricer.fra.DiscountingFraProductPricer;
import com.opengamma.strata.pricer.fx.DiscountingFxSwapProductPricer;
import com.opengamma.strata.pricer.index.DiscountingIborFutureProductPricer;
import com.opengamma.strata.pricer.index.DiscountingIborFutureTradePricer;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.deposit.IborFixingDepositTrade;
import com.opengamma.strata.product.deposit.ResolvedIborFixingDepositTrade;
import com.opengamma.strata.product.deposit.ResolvedTermDepositTrade;
import com.opengamma.strata.product.deposit.TermDepositTrade;
import com.opengamma.strata.product.fra.ResolvedFraTrade;
import com.opengamma.strata.product.fx.ResolvedFxSwapTrade;
import com.opengamma.strata.product.index.IborFutureTrade;
import com.opengamma.strata.product.index.ResolvedIborFutureTrade;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;

import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.ToDoubleBiFunction;

/**
 * Returns types from com.opengamma.strata.pricer.curve that are Serializable.
 */
public class CalibrationMeasureSerializables {

    public static final CalibrationMeasures MARKET_QUOTE = CalibrationMeasures.of("MarketQuote", fraMq(), iborFixingDepositMq(), iborFutureMq(), swapMq(), termDepositMq());

    public static final CalibrationMeasures PAR_SPREAD = CalibrationMeasures.of("ParSpread", fraParSpread(), fxSwapParSpread(), iborFixingDepositParSpread(), iborFutureParSpread(), swapParSpread(), termDepositParSpread());

    public static final CalibrationMeasures PRESENT_VALUE = CalibrationMeasures.of("PresentValue", fraPv(), iborFixingDepositPv(), iborFuturePv(), swapPv(), termDepositPv());

    /**
     * The calibrator for {@link ResolvedFraTrade} using par spread discounting.
     */
    private static TradeCalibrationMeasure<ResolvedFraTrade> fraParSpread() {
        return TradeCalibrationMeasure.of(
                "FraParSpreadDiscounting",
                ResolvedFraTrade.class,
                (ToDoubleBiFunction<ResolvedFraTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingFraProductPricer.DEFAULT.parSpread(trade.getProduct(), p),
                (BiFunction<ResolvedFraTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingFraProductPricer.DEFAULT.parSpreadSensitivity(trade.getProduct(), p));
    }

    /**
     * The calibrator for {@link ResolvedIborFutureTrade} using par spread discounting.
     */
    private static TradeCalibrationMeasure<ResolvedIborFutureTrade> iborFutureParSpread() {
        return TradeCalibrationMeasure.of(
                "IborFutureParSpreadDiscounting",
                ResolvedIborFutureTrade.class,
                (ToDoubleBiFunction<ResolvedIborFutureTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingIborFutureTradePricer.DEFAULT.parSpread(trade, p, 0.0),
                (BiFunction<ResolvedIborFutureTrade, RatesProvider, PointSensitivities> & Serializable) DiscountingIborFutureTradePricer.DEFAULT::parSpreadSensitivity);
    }

    /**
     * The calibrator for {@link ResolvedSwapTrade} using par spread discounting.
     */
    private static TradeCalibrationMeasure<ResolvedSwapTrade> swapParSpread() {
        return TradeCalibrationMeasure.of(
                "SwapParSpreadDiscounting",
                ResolvedSwapTrade.class,
                (ToDoubleBiFunction<ResolvedSwapTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingSwapProductPricer.DEFAULT.parSpread(trade.getProduct(), p),
                (BiFunction<ResolvedSwapTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingSwapProductPricer.DEFAULT.parSpreadSensitivity(trade.getProduct(), p).build());
    }

    /**
     * The calibrator for {@link ResolvedIborFixingDepositTrade} using par spread discounting.
     */
    private static TradeCalibrationMeasure<ResolvedIborFixingDepositTrade> iborFixingDepositParSpread() {
        return TradeCalibrationMeasure.of(
                "IborFixingDepositParSpreadDiscounting",
                ResolvedIborFixingDepositTrade.class,
                (ToDoubleBiFunction<ResolvedIborFixingDepositTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.parSpread(trade.getProduct(), p),
                (BiFunction<ResolvedIborFixingDepositTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.parSpreadSensitivity(trade.getProduct(), p));
    }

    /**
     * The calibrator for {@link ResolvedTermDepositTrade} using par spread discounting.
     */
    private static TradeCalibrationMeasure<ResolvedTermDepositTrade> termDepositParSpread() {
        return TradeCalibrationMeasure.of(
                "TermDepositParSpreadDiscounting",
                ResolvedTermDepositTrade.class,
                (ToDoubleBiFunction<ResolvedTermDepositTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingTermDepositProductPricer.DEFAULT.parSpread(trade.getProduct(), p),
                (BiFunction<ResolvedTermDepositTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingTermDepositProductPricer.DEFAULT.parSpreadSensitivity(trade.getProduct(), p));
    }

    /**
     * The calibrator for {@link ResolvedFxSwapTrade} using par spread discounting.
     */
    private static TradeCalibrationMeasure<ResolvedFxSwapTrade> fxSwapParSpread() {
        return TradeCalibrationMeasure.of(
                "FxSwapParSpreadDiscounting",
                ResolvedFxSwapTrade.class,
                (ToDoubleBiFunction<ResolvedFxSwapTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingFxSwapProductPricer.DEFAULT.parSpread(trade.getProduct(), p),
                (BiFunction<ResolvedFxSwapTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingFxSwapProductPricer.DEFAULT.parSpreadSensitivity(trade.getProduct(), p));
    }

    /**
     * The measure for {@link ResolvedFraTrade} using par rate discounting.
     */
    private static MarketQuoteMeasure<ResolvedFraTrade> fraMq() {
        return MarketQuoteMeasure.of(
                "FraParRateDiscounting",
                ResolvedFraTrade.class,
                (ToDoubleBiFunction<ResolvedFraTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingFraProductPricer.DEFAULT.parRate(trade.getProduct(), p),
                (BiFunction<ResolvedFraTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingFraProductPricer.DEFAULT.parRateSensitivity(trade.getProduct(), p));
    }

    /**
     * The measure for {@link ResolvedIborFutureTrade} using price discounting.
     */
    private static MarketQuoteMeasure<ResolvedIborFutureTrade> iborFutureMq() {
        return MarketQuoteMeasure.of(
                "IborFuturePriceDiscounting",
                ResolvedIborFutureTrade.class,
                (ToDoubleBiFunction<ResolvedIborFutureTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingIborFutureProductPricer.DEFAULT.price(trade.getProduct(), p),
                (BiFunction<ResolvedIborFutureTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingIborFutureProductPricer.DEFAULT.priceSensitivity(trade.getProduct(), p));
    }

    /**
     * The measure for {@link ResolvedSwapTrade} using par rate discounting. Apply only to swap with a fixed leg.
     */
    private static MarketQuoteMeasure<ResolvedSwapTrade> swapMq() {
        return MarketQuoteMeasure.of( // Market quote
                "SwapParRateDiscounting",
                ResolvedSwapTrade.class,
                (ToDoubleBiFunction<ResolvedSwapTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingSwapProductPricer.DEFAULT.parRate(trade.getProduct(), p),
                (BiFunction<ResolvedSwapTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingSwapProductPricer.DEFAULT.parRateSensitivity(trade.getProduct(), p).build());
    }

    /**
     * The measure for {@link ResolvedIborFixingDepositTrade} using par rate discounting.
     */
    private static MarketQuoteMeasure<ResolvedIborFixingDepositTrade> iborFixingDepositMq() {
        return MarketQuoteMeasure.of(
                "IborFixingDepositParRateDiscounting",
                ResolvedIborFixingDepositTrade.class,
                (ToDoubleBiFunction<ResolvedIborFixingDepositTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.parRate(trade.getProduct(), p),
                (BiFunction<ResolvedIborFixingDepositTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.parRateSensitivity(trade.getProduct(), p));
    }

    /**
     * The measure for {@link ResolvedTermDepositTrade} using par rate discounting.
     */
    private static MarketQuoteMeasure<ResolvedTermDepositTrade> termDepositMq() {
        return MarketQuoteMeasure.of(
                "TermDepositParRateDiscounting",
                ResolvedTermDepositTrade.class,
                (ToDoubleBiFunction<ResolvedTermDepositTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingTermDepositProductPricer.DEFAULT.parRate(trade.getProduct(), p),
                (BiFunction<ResolvedTermDepositTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingTermDepositProductPricer.DEFAULT.parRateSensitivity(trade.getProduct(), p));
    }

    private static PresentValueCalibrationMeasure<ResolvedFraTrade> fraPv() {
        return PresentValueCalibrationMeasure.of(
                "FraPresentValueDiscounting",
                ResolvedFraTrade.class,
                (ToDoubleBiFunction<ResolvedFraTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingFraProductPricer.DEFAULT.presentValue(trade.getProduct(), p).getAmount(),
                (BiFunction<ResolvedFraTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingFraProductPricer.DEFAULT.presentValueSensitivity(trade.getProduct(), p));
    }

    /**
     * The calibrator for {@link IborFutureTrade} using par spread discounting.
     */
    private static PresentValueCalibrationMeasure<ResolvedIborFutureTrade> iborFuturePv() {
        return PresentValueCalibrationMeasure.of(
                "IborFutureParSpreadDiscounting",
                ResolvedIborFutureTrade.class,
                (ToDoubleBiFunction<ResolvedIborFutureTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingIborFutureTradePricer.DEFAULT.presentValue(trade, p, 0.0).getAmount(),
                (BiFunction<ResolvedIborFutureTrade, RatesProvider, PointSensitivities> & Serializable) DiscountingIborFutureTradePricer.DEFAULT::presentValueSensitivity);
    }

    /**
     * The calibrator for {@link SwapTrade} using par spread discounting.
     */
    private static PresentValueCalibrationMeasure<ResolvedSwapTrade> swapPv() {
        return PresentValueCalibrationMeasure.of(
                "SwapParSpreadDiscounting",
                ResolvedSwapTrade.class,
                (ToDoubleBiFunction<ResolvedSwapTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingSwapProductPricer.DEFAULT.presentValue(trade.getProduct(), p).convertedTo(trade.getProduct().getLegs().get(0).getCurrency(), p).getAmount(),
                (BiFunction<ResolvedSwapTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingSwapProductPricer.DEFAULT.presentValueSensitivity(trade.getProduct(), p).build().convertedTo(trade.getProduct().getLegs().get(0).getCurrency(), p));
    }

    /**
     * The calibrator for {@link IborFixingDepositTrade} using par spread discounting.
     */
    private static PresentValueCalibrationMeasure<ResolvedIborFixingDepositTrade> iborFixingDepositPv() {
        return PresentValueCalibrationMeasure.of(
                "IborFixingDepositParSpreadDiscounting",
                ResolvedIborFixingDepositTrade.class,
                (ToDoubleBiFunction<ResolvedIborFixingDepositTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.presentValue(trade.getProduct(), p).getAmount(),
                (BiFunction<ResolvedIborFixingDepositTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingIborFixingDepositProductPricer.DEFAULT.presentValueSensitivity(trade.getProduct(), p));
    }

    /**
     * The calibrator for {@link TermDepositTrade} using par spread discounting.
     */
    private static PresentValueCalibrationMeasure<ResolvedTermDepositTrade> termDepositPv() {
        return PresentValueCalibrationMeasure.of(
                "TermDepositParSpreadDiscounting",
                ResolvedTermDepositTrade.class,
                (ToDoubleBiFunction<ResolvedTermDepositTrade, RatesProvider> & Serializable) (trade, p) -> DiscountingTermDepositProductPricer.DEFAULT.presentValue(trade.getProduct(), p).getAmount(),
                (BiFunction<ResolvedTermDepositTrade, RatesProvider, PointSensitivities> & Serializable) (trade, p) -> DiscountingTermDepositProductPricer.DEFAULT.presentValueSensitivity(trade.getProduct(), p));
    }
}

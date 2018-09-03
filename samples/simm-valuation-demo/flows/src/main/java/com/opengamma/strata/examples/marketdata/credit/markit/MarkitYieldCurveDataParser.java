package com.opengamma.strata.examples.marketdata.credit.markit;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharSource;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.io.CsvFile;
import com.opengamma.strata.collect.io.CsvRow;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.pricer.credit.IsdaYieldCurveInputs;
import com.opengamma.strata.pricer.credit.IsdaYieldCurveInputsId;
import com.opengamma.strata.pricer.credit.IsdaYieldCurveUnderlyingType;
import com.opengamma.strata.product.credit.type.IsdaYieldCurveConvention;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Parser to load daily yield curve information provided by Markit.
 * <p>
 * The columns are defined as
 * {@code Valuation Date,Tenor,Instrument Type,Rate,Curve Convention}.
 */
public class MarkitYieldCurveDataParser {

    private static final String DATE = "Valuation Date";
    private static final String TENOR = "Tenor";
    private static final String INSTRUMENT = "Instrument Type";
    private static final String RATE = "Rate";
    private static final String CONVENTION = "Curve Convention";

    /**
     * Parses the specified source.
     *
     * @param source the source to parse
     * @return the map of parsed yield curve par rates
     */
    public static Map<IsdaYieldCurveInputsId, IsdaYieldCurveInputs> parse(CharSource source) {
        // parse the curve data
        Map<IsdaYieldCurveConvention, List<Point>> curveData = Maps.newHashMap();
        CsvFile csv = CsvFile.of(source, true);
        for (CsvRow row : csv.rows()) {
            String dateText = row.getField(DATE);
            String tenorText = row.getField(TENOR);
            String instrumentText = row.getField(INSTRUMENT);
            String rateText = row.getField(RATE);
            String conventionText = row.getField(CONVENTION);

            Point point = new Point(
                    Tenor.parse(tenorText),
                    LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE),
                    mapUnderlyingType(instrumentText),
                    Double.parseDouble(rateText));
            IsdaYieldCurveConvention convention = IsdaYieldCurveConvention.of(conventionText);

            List<Point> points = curveData.computeIfAbsent(convention, k -> Lists.newArrayList());
            points.add(point);
        }

        // convert the curve data into the result map
        Map<IsdaYieldCurveInputsId, IsdaYieldCurveInputs> result = Maps.newHashMap();
        for (Map.Entry<IsdaYieldCurveConvention, List<Point>> isdaYieldCurveConventionListEntry : curveData.entrySet()) {
            List<Point> points = isdaYieldCurveConventionListEntry.getValue();
            result.put(IsdaYieldCurveInputsId.of((isdaYieldCurveConventionListEntry.getKey()).getCurrency()),
                    IsdaYieldCurveInputs.of(
                            CurveName.of((isdaYieldCurveConventionListEntry.getKey()).getName()),
                            points.stream().map(s -> s.getTenor().getPeriod()).toArray(Period[]::new),
                            points.stream().map(s -> s.getDate()).toArray(LocalDate[]::new),
                            points.stream().map(s -> s.getInstrumentType()).toArray(IsdaYieldCurveUnderlyingType[]::new),
                            points.stream().mapToDouble(s -> s.getRate()).toArray(),
                            isdaYieldCurveConventionListEntry.getKey()));
        }
        return result;
    }

    // parse the M/S instrument type flag
    private static IsdaYieldCurveUnderlyingType mapUnderlyingType(String type) {
        switch (type) {
            case "M":
                return IsdaYieldCurveUnderlyingType.ISDA_MONEY_MARKET;
            case "S":
                return IsdaYieldCurveUnderlyingType.ISDA_SWAP;
            default:
                throw new IllegalStateException("Unknown underlying type, only M or S allowed: " + type);
        }
    }

    //-------------------------------------------------------------------------

    /**
     * Stores the parsed data points.
     */
    private static class Point {
        private final Tenor tenor;
        private final LocalDate date;
        private final IsdaYieldCurveUnderlyingType instrumentType;
        private final double rate;

        private Point(Tenor tenor, LocalDate baseDate, IsdaYieldCurveUnderlyingType instrumentType, double rate) {
            this.tenor = tenor;
            this.date = baseDate.plus(tenor.getPeriod());
            this.instrumentType = instrumentType;
            this.rate = rate;
        }

        public Tenor getTenor() {
            return tenor;
        }

        public LocalDate getDate() {
            return date;
        }

        public IsdaYieldCurveUnderlyingType getInstrumentType() {
            return instrumentType;
        }

        public double getRate() {
            return rate;
        }
    }

}

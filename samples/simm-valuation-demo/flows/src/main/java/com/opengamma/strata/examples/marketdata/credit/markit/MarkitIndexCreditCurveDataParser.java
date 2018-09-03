package com.opengamma.strata.examples.marketdata.credit.markit;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharSource;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.io.CsvFile;
import com.opengamma.strata.collect.io.CsvRow;
import com.opengamma.strata.data.ImmutableMarketDataBuilder;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.pricer.credit.CdsRecoveryRate;
import com.opengamma.strata.pricer.credit.IsdaCreditCurveInputs;
import com.opengamma.strata.pricer.credit.IsdaIndexCreditCurveInputsId;
import com.opengamma.strata.pricer.credit.IsdaIndexRecoveryRateId;
import com.opengamma.strata.product.credit.IndexReferenceInformation;
import com.opengamma.strata.product.credit.type.CdsConvention;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser to load daily index curve information provided by Markit.
 * <p>
 * The columns are defined as {@code
 * Date,Name,Series,Version,Term,
 * RED Code,Index ID,Maturity,On The Run,Composite Price,
 * Composite Spread,Model Price,Model Spread,Depth,Heat}.
 * <p>
 * Also reads static data from a csv file
 * <p>
 * RedCode,From Date,Convention,Recovery Rate,Index Factor
 */
public class MarkitIndexCreditCurveDataParser {

    // Markit date format with the month in full caps. e.g. 11-JUL-14
    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("dd-MMM-uu").toFormatter(Locale.ENGLISH);

    enum Columns {

        Series("Series"),
        Version("Version"),
        Term("Term"),
        RedCode("RED Code"),
        Maturity("Maturity"),
        CompositeSpread("Composite Spread"),
        ModelSpread("Model Spread");

        private final String columnName;

        Columns(String columnName) {
            this.columnName = columnName;
        }

        public String getColumnName() {
            return columnName;
        }
    }

    /**
     * Parses the specified sources.
     *
     * @param builder  the market data builder that the resulting curve and recovery rate items should be loaded into
     * @param curveSource  the source of curve data to parse
     * @param staticDataSource  the source of static data to parse
     */
    public static void parse(
            ImmutableMarketDataBuilder builder,
            CharSource curveSource,
            CharSource staticDataSource) {

        Map<IsdaIndexCreditCurveInputsId, List<Point>> curveData = Maps.newHashMap();
        Map<MarkitRedCode, StaticData> staticDataMap = parseStaticData(staticDataSource);

        CsvFile csv = CsvFile.of(curveSource, true);
        for (CsvRow row : csv.rows()) {
            String seriesText = row.getField(Columns.Series.getColumnName());
            String versionText = row.getField(Columns.Version.getColumnName());
            String termText = row.getField(Columns.Term.getColumnName());
            String redCodeText = row.getField(Columns.RedCode.getColumnName());
            String maturityText = row.getField(Columns.Maturity.getColumnName());
            String compositeSpreadText = row.getField(Columns.CompositeSpread.getColumnName());
            String modelSpreadText = row.getField(Columns.ModelSpread.getColumnName());

            StandardId indexId = MarkitRedCode.id(redCodeText);
            int indexSeries = Integer.parseInt(seriesText);
            int indexAnnexVersion = Integer.parseInt(versionText);

            IsdaIndexCreditCurveInputsId id = IsdaIndexCreditCurveInputsId.of(
                    IndexReferenceInformation.of(
                            indexId,
                            indexSeries,
                            indexAnnexVersion));

            Tenor term = Tenor.parse(termText);
            LocalDate maturity = LocalDate.parse(maturityText, DATE_FORMAT);

            double spread;
            if (compositeSpreadText.isEmpty()) {
                if (modelSpreadText.isEmpty()) {
                    // there is no rate for this row, continue
                    continue;
                }
                // fall back to the model rate is the composite is missing
                spread = parseRate(modelSpreadText);
            } else {
                // prefer the composite rate if it is present
                spread = parseRate(compositeSpreadText);
            }

            List<Point> points = curveData.computeIfAbsent(id, k -> Lists.newArrayList());
            points.add(new Point(term, maturity, spread));
        }

        for (Map.Entry<IsdaIndexCreditCurveInputsId, List<Point>> isdaIndexCreditCurveInputsIdListEntry : curveData.entrySet()) {
            MarkitRedCode redCode = MarkitRedCode.from((isdaIndexCreditCurveInputsIdListEntry.getKey()).getReferenceInformation().getIndexId());
            StaticData staticData = staticDataMap.get(redCode);
            ArgChecker.notNull(staticData, "Did not find a static data record for " + redCode);
            CdsConvention convention = staticData.getConvention();
            double recoveryRate = staticData.getRecoveryRate();
            double indexFactor = staticData.getIndexFactor();
            // TODO add fromDate handling

            String creditCurveName = (isdaIndexCreditCurveInputsIdListEntry.getKey()).toString();

            List<Point> points = isdaIndexCreditCurveInputsIdListEntry.getValue();

            Period[] periods = points.stream().map(s -> s.getTenor().getPeriod()).toArray(Period[]::new);
            LocalDate[] endDates = points.stream().map(s -> s.getDate()).toArray(LocalDate[]::new);
            double[] rates = points.stream().mapToDouble(s -> s.getRate()).toArray();

            IsdaCreditCurveInputs curveInputs = IsdaCreditCurveInputs.of(
                    CurveName.of(creditCurveName),
                    periods,
                    endDates,
                    rates,
                    convention,
                    indexFactor);

            builder.addValue(isdaIndexCreditCurveInputsIdListEntry.getKey(), curveInputs);

            IsdaIndexRecoveryRateId recoveryRateId = IsdaIndexRecoveryRateId.of((isdaIndexCreditCurveInputsIdListEntry.getKey()).getReferenceInformation());
            CdsRecoveryRate cdsRecoveryRate = CdsRecoveryRate.of(recoveryRate);

            builder.addValue(recoveryRateId, cdsRecoveryRate);
        }
    }

    // parses the static data file
    private static Map<MarkitRedCode, StaticData> parseStaticData(CharSource source) {
        CsvFile csv = CsvFile.of(source, true);

        Map<MarkitRedCode, StaticData> result = Maps.newHashMap();
        for (CsvRow row : csv.rows()) {
            String redCodeText = row.getField("RedCode");
            String fromDateText = row.getField("From Date");
            String conventionText = row.getField("Convention");
            String recoveryRateText = row.getField("Recovery Rate");
            String indexFactorText = row.getField("Index Factor");

            MarkitRedCode redCode = MarkitRedCode.of(redCodeText);
            LocalDate fromDate = LocalDate.parse(fromDateText, DATE_FORMAT);
            CdsConvention convention = CdsConvention.of(conventionText);
            double recoveryRate = parseRate(recoveryRateText);
            double indexFactor = Double.parseDouble(indexFactorText);

            result.put(redCode, new StaticData(fromDate, convention, recoveryRate, indexFactor));
        }
        return result;
    }

    //-------------------------------------------------------------------------

    /**
     * Stores the parsed static data.
     */
    private static class StaticData {

        private LocalDate fromDate;
        private CdsConvention convention;
        private double recoveryRate;
        private double indexFactor;

        private StaticData(LocalDate fromDate, CdsConvention convention, double recoveryRate, double indexFactor) {
            this.fromDate = fromDate;
            this.convention = convention;
            this.recoveryRate = recoveryRate;
            this.indexFactor = indexFactor;
        }

        @SuppressWarnings("unused")
        public LocalDate getFromDate() {
            return fromDate;
        }

        public CdsConvention getConvention() {
            return convention;
        }

        public double getRecoveryRate() {
            return recoveryRate;
        }

        public double getIndexFactor() {
            return indexFactor;
        }
    }

    //-------------------------------------------------------------------------

    /**
     * Stores the parsed data points.
     */
    private static class Point {
        private final Tenor tenor;

        private final LocalDate date;

        private final double rate;

        private Point(Tenor tenor, LocalDate date, double rate) {
            this.tenor = tenor;
            this.date = date;
            this.rate = rate;
        }

        public Tenor getTenor() {
            return tenor;
        }

        public LocalDate getDate() {
            return date;
        }

        public double getRate() {
            return rate;
        }
    }

    // Converts from a string percentage rate with a percent sign to a double rate
    // e.g. 0.12% => 0.0012d
    private static double parseRate(String input) {
        return Double.parseDouble(input.replace("%", "")) / 100d;
    }

}

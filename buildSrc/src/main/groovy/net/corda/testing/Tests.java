package net.corda.testing;

import groovy.lang.Tuple2;
import groovy.lang.Tuple3;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Tests {
    final static String TEST_NAME = "Test Name";
    final static String MEAN_DURATION_NANOS = "Mean Duration Nanos";
    final static String NUMBER_OF_RUNS = "Number of runs";

    // test name -> (mean duration, number of runs)
    private final Map<String, Tuple2<Long, Long>> tests = new HashMap<>();
    private Tuple2<Long, Long> mean = new Tuple2<>(1L, 1L);

    /**
     * Read tests, mean duration and runs from a csv file.
     * @param reader a reader
     * @return list of tests, or an empty list if none or we have a problem.
     */
    public static List<Tuple3<String, Long, Long>> read(Reader reader) {
        try {
            List<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(reader).getRecords();
            return records.stream().map(record -> {
                try {
                    final String testName = record.get(TEST_NAME);
                    final long testDuration = Long.parseLong(record.get(MEAN_DURATION_NANOS));
                    final long testRuns = Long.parseLong(record.get(NUMBER_OF_RUNS));
                    // don't allow times of 'zero'.  Minimum of 1.
                    return new Tuple3<>(testName, Math.max(testDuration, 1), Math.max(testRuns, 1));
                } catch (IllegalArgumentException | IllegalStateException e) {
                    return null;
                }
            }).filter(Objects::nonNull).sorted(Comparator.comparing(Tuple3::getFirst)).collect(Collectors.toList());
        } catch (IOException ignored) {

        }
        return Collections.emptyList();
    }

    //  TODO - remove
    public List<Tuple2<String, Long>> getAll() {
        List<Tuple2<String, Long>> results = new ArrayList<>();
        for (String s : tests.keySet()) {
            results.add(new Tuple2<>(s, tests.get(s).getFirst().longValue()));
        }
        return results;
    }

    /**
     * Write a csv file of test name, duration, runs
     * @param writer a writer
     * @return true if no problems.
     */
    public boolean write(@NotNull final Writer writer) {
        boolean ok = true;
        final CSVPrinter printer;
        try {
            printer = new CSVPrinter(writer,
                    CSVFormat.DEFAULT.withHeader(TEST_NAME, MEAN_DURATION_NANOS, NUMBER_OF_RUNS));
            for (String key : tests.keySet()) {
                printer.printRecord(key, tests.get(key).getFirst(), tests.get(key).getSecond());
            }

            printer.flush();
        } catch (IOException e) {
            ok = false;
        }
        return ok;
    }

    /**
     * Add tests, and also (re)calculate the mean test duration.
     * e.g. addTests(read(reader));
     *
     * @param testsCollection tests, typically from a csv file.
     */
    public void addTests(@NotNull final List<Tuple3<String, Long, Long>> testsCollection) {
        for (Tuple3<String, Long, Long> test : testsCollection) {
            this.tests.put(test.getFirst(), new Tuple2<>(test.getSecond(), test.getThird()));
        }

        // Calculate the mean test time.
        if (tests.size() > 0) {
            long total = 0;
            for (String testName : this.tests.keySet()) total += tests.get(testName).getFirst();
            mean = new Tuple2<>(total / this.tests.size(), 1L);
        }
    }

    /**
     * Get the known mean duration of a test.
     *
     * @param testName the test name
     * @return duration in nanos.
     */
    public long getDuration(@NotNull final String testName) {
        return tests.getOrDefault(testName, mean).getFirst();
    }

    /**
     * Add test information.  Recalulates mean test duration if already exists.
     *
     * @param testName name of the test
     * @param durationNanos duration
     */
    public void addDuration(@NotNull final String testName, long durationNanos) {
        Tuple2<Long, Long> current = tests.getOrDefault(testName, new Tuple2<>(0L, 0L));
        final long total = current.getFirst() * current.getSecond() + durationNanos;
        final long count = current.getSecond() + 1;

        tests.put(testName, new Tuple2<>(total / count, count));
    }

    /**
     * Do we have any test information?
     * @return false if no tests info
     */
    public boolean isEmpty() {
        return tests.isEmpty();
    }

    /**
     * How many tests do we have?
     * @return the number of tests we have information for
     */
    public int size() {
        return tests.size();
    }

    /**
     * Return all tests (and their durations) that being with (or are equal to) `testPrefix`
     * @param testPrefix could be just the classname, or the entire classname + testname.
     * @return list of matching tests
     */
    List<Tuple2<String, Long>> startsWith(@NotNull final String testPrefix) {
        final List<Tuple2<String, Long>> results = new ArrayList<>();

        for (String test : this.tests.keySet()) {
            if (test.startsWith(testPrefix)) {
                results.add(new Tuple2<>(test, getDuration(test)));
            }
        }
        return results;
    }
}
package net.corda.testing;

import groovy.lang.Tuple2;
import groovy.lang.Tuple3;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Tests {
    final static String TEST_NAME = "Test Name";
    final static String MEAN_DURATION_NANOS = "Mean Duration Nanos";
    final static String NUMBER_OF_RUNS = "Number of runs";
    private static final Logger LOG = LoggerFactory.getLogger(Tests.class);
    // test name -> (mean duration, number of runs)
    private final Map<String, Tuple2<Long, Long>> tests = new HashMap<>();
    // mean, count
    private Tuple2<Long, Long> meanForTests = new Tuple2<>(1L, 0L);
    // as we add more tests with the same className prefix, maintain the mean duration for classes.
    private long meanDurationForClasses = 1L;
    // All the unique classNames of all the tests
    private Set<String> classNames = new HashSet<>();

    /**
     * Read tests, mean duration and runs from a csv file.
     *
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

    private static Tuple2<Long, Long> recalculateMean(@NotNull final Tuple2<Long, Long> previous, long nanos) {
        final long total = previous.getFirst() * previous.getSecond() + nanos;
        final long count = previous.getSecond() + 1;
        return new Tuple2<>(total / count, count);
    }

    /**
     * Write a csv file of test name, duration, runs
     *
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
     * Get the classname of a test, if it has one, otherwise return ""
     *
     * @param testName the testname with optional classname, e.g. com.foo.thisTest
     * @return com.foo else ""
     */
    private String getClassName(@NotNull final String testName) {
        return testName.lastIndexOf('.') != -1 ?
                testName.substring(0, testName.lastIndexOf('.')) : "";
    }

    /**
     * Add tests, and also (re)calculate the mean test duration.
     * e.g. addTests(read(reader));
     *
     * @param testsCollection tests, typically from a csv file.
     */
    public void addTests(@NotNull final List<Tuple3<String, Long, Long>> testsCollection) {
        for (Tuple3<String, Long, Long> test : testsCollection) {
            final String testName = test.getFirst();
            this.tests.put(testName, new Tuple2<>(test.getSecond(), test.getThird()));

            classNames.add(getClassName(testName));
        }

        // Calculate the mean test time.
        if (tests.size() > 0) {
            long total = 0;
            for (String testName : this.tests.keySet()) total += tests.get(testName).getFirst();
            meanForTests = new Tuple2<>(total / this.tests.size(), 1L);
            meanDurationForClasses = (meanForTests.getFirst() * tests.size()) / classNames.size();
        }
    }

    /**
     * Get the known mean duration of a test.
     *
     * @param testName the test name
     * @return duration in nanos.
     */
    public long getDuration(@NotNull final String testName) {
        return tests.getOrDefault(testName, meanForTests).getFirst();
    }

    /**
     * Add test information.  Recalulates mean test duration if already exists.
     *
     * @param testName      name of the test
     * @param durationNanos duration
     */
    public void addDuration(@NotNull final String testName, long durationNanos) {
        final Tuple2<Long, Long> current = tests.getOrDefault(testName, new Tuple2<>(0L, 0L));

        tests.put(testName, recalculateMean(current, durationNanos));
        meanForTests = recalculateMean(meanForTests, durationNanos);

        classNames.add(getClassName(testName));

        meanDurationForClasses = (meanForTests.getFirst() * tests.size()) / classNames.size();
    }

    /**
     * Get the mean duration for a class of unit tests.
     * This is simply (the mean test duration  *  the number of known tests)  /  number of classes
     *
     * @return mean duration for a class of unit tests to execute in nanos.
     */
    public long getMeanDurationForClasses() {
        return meanDurationForClasses;
    }

    /**
     * Do we have any test information?
     *
     * @return false if no tests info
     */
    public boolean isEmpty() {
        return tests.isEmpty();
    }

    /**
     * How many tests do we have?
     *
     * @return the number of tests we have information for
     */
    public int size() {
        return tests.size();
    }

    /**
     * Return all tests (and their durations) that being with (or are equal to) `testPrefix`
     *
     * @param testPrefix could be just the classname, or the entire classname + testname.
     * @return list of matching tests
     */
    @NotNull
    List<Tuple2<String, Long>> startsWith(@NotNull final String testPrefix) {
        final List<Tuple2<String, Long>> results = new ArrayList<>();

        for (String test : this.tests.keySet()) {
            if (test.startsWith(testPrefix)) {
                results.add(new Tuple2<>(test, getDuration(test)));
            }
        }
        return results;
    }

    /**
     * How many times has this function been run?  Every call to addDuration increments the current value.
     *
     * @param testName the test name
     * @return the number of times the test name has been run.
     */
    public long getRunCount(@NotNull final String testName) {
        return tests.getOrDefault(testName, new Tuple2<>(0L, 0L)).getSecond();
    }

    /**
     * Return the mean duration for a unit to run
     *
     * @return mean duration in nanos.
     */
    public long getMeanDurationForTests() {
        return meanForTests.getFirst();
    }

    /**
     * Clear all tests
     */
    public void clear() {
        tests.clear();
        classNames.clear();
        meanForTests = new Tuple2<>(1L, 0L);
        meanDurationForClasses = 1L;
    }
}
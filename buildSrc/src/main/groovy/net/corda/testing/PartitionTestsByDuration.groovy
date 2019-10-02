package net.corda.testing

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.slf4j.LoggerFactory

import java.util.stream.Collectors

/** Aim to partition tests into roughly equal total-duration partitions, using a greedy algorithm
 *
 * This wants to be pretty quick as this is part of the build.
 *
 * https://en.wikipedia.org/wiki/Partition_problem#The_greedy_algorithm
 *
 * We just loop through each test (sorted by duration, longest to shortest), then just allocate to the current smallest bin.
 *
 * According to wikipedia, the greedy solution, tends to max set sizes 4/3 (1.333) times the optimal solution
 * as the number of sets gets large.
 *
 * In other words, for a many partitions with an optimal size of 30 minutes, the greedy solution can lead to a max partition of 40 minutes
 *
 * This is a useful (local) command line test:  ./gradlew -Dkubenetize  :core:printTestsForTest  -PdockerFork=1 -PdockerForks=11
 */
class PartitionTestsByDuration {
    private static def logger = LoggerFactory.getLogger(Class.getSimpleName())

    // We populate this - we take the input set of tests, and figure out their duration, and then
    // sort by decreasing value, if we don't find a test, we assign it the mean duration.
    List<UnitTest> allTestsSortedByDuration

    // We calculate these
    List<Partition> partitions

    double defaultDuration = 0.0

    // Penalty time value for 'zero duration' tests.
    static double PENALTY = 1.0

    class Partition {
        List<String> names = new ArrayList<>()

        // time units from TeamCity are milliseconds
        double totalDuration = 0.0

        void add(String test, double duration) {
            names.add(test)
            totalDuration += duration
        }
    }

    PartitionTestsByDuration(int partitions, List<String> allTests, Map<String, Double> durationByTest) {
        if (durationByTest.empty) throw new IllegalArgumentException("Expected at least some tests with durations")
        if (allTests.isEmpty()) throw new IllegalArgumentException("Expected at least some tests to partition")

        // Get the average test duration and use it for tests that we don't know about.
        this.defaultDuration = durationByTest.values().stream().mapToDouble({ v -> v.doubleValue() }).average().orElse(0.0);
        def unsortedTests = new ArrayList<UnitTest>()

        // Look up duration for all tests, and use default value otherwise
        allTests.forEach { testName ->
            def duration = durationByTest.getOrDefault(testName, this.defaultDuration)

            // Zero time tests don't take zero time, and probably have unrecorded overhead (e.g. setup/teardown), so given them
            // a penalty value so that they're equally distributed across all partitions.
            if (duration < 1.0)  { duration = PENALTY }

            unsortedTests.add(new UnitTest(name: testName, duration: duration))
        }

        // Sort by decreasing duration
        this.allTestsSortedByDuration = unsortedTests.sort { lhs, rhs -> rhs.duration - lhs.duration }

        fillPartitions(partitions)
    }

    double durationOf(String testName) {
        Optional<UnitTest> unitTest = this.allTestsSortedByDuration.stream()
                .filter({ t -> t.name == testName })
                .findFirst()

        if (unitTest.present)
            return unitTest.get().duration

        return this.defaultDuration
    }

    /**
     * Human readable info
     * @param partitionIdx
     * @return
     */
    String partitionDuration(int partitionIdx) {
        def duration = partitionIdx >= partitions.size() ?
                Collections.max(partitions, Comparator.comparing({ p -> p.totalDuration })).totalDuration
                : partitions.get(partitionIdx).totalDuration
        if (duration > 60000) {
            return ((int) (duration / 60000)).toString() + " minutes"
        } else if (duration > 1000) {
            return ((int) (duration / 1000)).toString() + " seconds"
        } else {
            return duration.toString() + " millis"
        }
    }

    String summary() { return summary(-1) }

    String summary(int partitionIdx) {
        def builder = new StringBuilder()
        builder.append("Total tests to be run:  ").append(this.allTestsSortedByDuration.size()).append("\n")

        String result = this.allTestsSortedByDuration.stream()
                .map({ '"' + it.name + '"'})
                .collect(Collectors.joining(","))

        logger.debug("All detected tests: ")
        logger.debug("[" + result + "]")

        for (int i = 0; i < this.partitions.size(); ++i) {
            builder
                    .append(i == partitionIdx ? ">>  " : "    ")
                    .append("Partition ")
                    .append(String.format("%5d", i))
                    .append("  Tests:  ")
                    .append(String.format("%5d", this.getAllTestsForPartition(i).size()))
                    .append("  Approx duration:  ")
                    .append(partitionDuration(i))
                    .append('\n')
        }

        return builder.toString()
    }

    double getDuration(int partitionIdx) {
        if (partitionIdx >= partitions.size()) throw new IllegalArgumentException("Asked for partition greater than total partitions")

        return this.partitions.get(partitionIdx).totalDuration
    }

    List<String> getAllTestsForPartition(int partitionIdx) {
        if (partitionIdx >= partitions.size()) throw new IllegalArgumentException("Asked for partition greater than total partitions")

        return partitions.get(partitionIdx).names
    }

    List<String> getProjectOnlyTestsForPartition(int partitionIdx, List<String> projectTests) {
        if (partitionIdx >= partitions.size()) throw new IllegalArgumentException("Asked for partition greater than total partitions")

        def copy = new ArrayList(getAllTestsForPartition(partitionIdx))
        copy.retainAll(projectTests)
        return copy
    }

    private void fillPartitions(int partitionCount) {
        logger.debug('Allocating tests to {} partitions', partitionCount)

        partitions = new ArrayList<>()
        for (int i = 0; i < partitionCount; ++i) {
            partitions.add(new Partition())
        }

        // For each test, get its duration, and then add it to the smallest partition.
        // This is the partition-problem greedy algorithm solution.
        this.allTestsSortedByDuration.forEach { UnitTest unitTest ->
            Collections.min(partitions, Comparator.comparing({ p -> p.totalDuration }))
                    .add(unitTest.name, unitTest.duration)
        }

        partitions.forEach({ b -> logger.debug('Tests in this partition: {},  approx. runtime: {}ms', b.names.size(), b.totalDuration) })
    }
}
/**
 * Trivial class to hold the testName and the duration it took, typically loaded from a TeamCity csv file.
 */
class UnitTest {
    private static def logger = LoggerFactory.getLogger(Class.getSimpleName())

    String name
    double duration

    @Override
    String toString() {
        return "UnitTest{" +
                "name='" + name + '\'' +
                ", duration=" + duration +
                '}'
    }

    /**
     * Get the test durations from a TeamCity test file in csv format.
     * 2nd column is 'Test Name'
     * 4th column is 'Duration(ms)'
     * @param path
     * @return empty set if no tests, or a problem parsing csv
     */
    static Map<String, Double> fromTeamCityCsv(Reader reader) {
        def tests = new HashMap<String, Double>()

        try {
            def name = "Test Name"
            def duration = "Duration(ms)"
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(reader)
            for (CSVRecord record : records) {
                String testName = record.get(name)
                String testDuration = record.get(duration)
                tests.put(testName, Double.parseDouble(testDuration))
            }
        } catch (IllegalArgumentException | IllegalStateException | IOException | NumberFormatException e) {
            logger.warn('Problem parsing csv: {}', e.getMessage())
            tests.clear()
        }

//        logger.debug("Tests in csv file:  {}", tests.toString())
        logger.info("Test count in csv:  {} tests", tests.size())
        return tests
    }
}

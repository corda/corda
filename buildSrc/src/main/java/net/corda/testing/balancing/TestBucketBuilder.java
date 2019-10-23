package net.corda.testing.balancing;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Given a collection of test timings, and a collection of discovered tests, organise the test timings into test buckets by test name.
 * If there are no timings recorded for a test, create an empty bucket for it anyway.
 * Makes use of ordering by name on the two collections for efficient lookup.
 */
final class TestBucketBuilder {

    static @NotNull TestBucketBuilder using(@NotNull Stream<TestTiming> testTimings) {
        // Organise test timings into an ordered map by test name
        Map<String, List<TestTiming>> testTimingsByName = testTimings.collect(
                Collectors.groupingBy(TestTiming::getName, TreeMap::new, Collectors.toList()));

        return new TestBucketBuilder(PeekableIterator.on(testTimingsByName.entrySet()));
    }

    private final @NotNull PeekableIterator<Map.Entry<String, List<TestTiming>>> testTimingsByName;

    private TestBucketBuilder(@NotNull PeekableIterator<Map.Entry<String, List<TestTiming>>> testTimingsByName) {
        this.testTimingsByName = testTimingsByName;
    }

    @NotNull Stream<TestBucket> getTestBuckets(@NotNull Collection<TestSource> sources) {
        // Assumes that test names are unique across different tasks, which might conceivably not be true.
        Stream<DiscoveredTest> allDiscoveredTests = sources.stream()
                .flatMap(TestSource::getDiscoveredTests)
                .sorted(Comparator.comparing(DiscoveredTest::getName));

        return allDiscoveredTests
                .map(discoveredTest -> makeTestBucket(discoveredTest.getName(), discoveredTest.getTask()))
                .sorted(Comparator.comparing(TestBucket::getDuration).reversed());
    }

    private @NotNull TestBucket makeTestBucket(@NotNull String discoveredTestName, @NotNull Object task) {
        List<TestTiming> matchingTestTimings = new ArrayList<>();

        // Roll through the test timings and add them to matchingTestTimings, for as long as they match to the discovered test name.
        while (testTimingsByName.hasNext()) {
            Map.Entry<String, List<TestTiming>> nextEntry = testTimingsByName.peek();

            if (!nextEntry.getKey().startsWith(discoveredTestName)) {
                break;
            }

            matchingTestTimings.addAll(nextEntry.getValue());
            testTimingsByName.next();
        }

        return TestBucket.of(task, discoveredTestName, matchingTestTimings);
    }
}

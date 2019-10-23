package net.corda.testing.balancing;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Distributes test buckets among fork containers, then uses them to generate a TestPlan.
 */
final class TestPlanner {

    static @NotNull TestPlanner forForkCount(int forkCount) {
        return new TestPlanner(forkCount);
    }

    private final int forkCount;

    private TestPlanner(int forkCount) {
        this.forkCount = forkCount;
    }

    @NotNull TestPlan createPlan(@NotNull Stream<TestBucket> testBuckets) {
        // Initialize fork containers
        List<TestsForForkContainer> forks = IntStream.range(0, forkCount)
                .mapToObj(TestsForForkContainer::new)
                .collect(Collectors.toList());

        // Populate with tests from buckets
        testBuckets.forEach(matchedTestBucket -> {
            TestsForForkContainer smallestContainer = Collections.min(forks, Comparator.comparing(TestsForForkContainer::getCurrentDuration));
            smallestContainer.addBucket(matchedTestBucket);
        });

        // Generate test plan
        return TestPlan.forForks(forks);
    }
}

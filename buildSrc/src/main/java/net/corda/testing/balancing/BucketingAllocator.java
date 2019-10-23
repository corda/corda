package net.corda.testing.balancing;

import net.corda.testing.listing.TestLister;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Accumulates test sources, and uses provided test timings to generate a test plan once all sources are captured.
 */
class BucketingAllocator {

    static @NotNull BucketingAllocator create(int forkCount, @NotNull Supplier<Stream<TestTiming>> timingsProvider) {
        TestPlanner testPlanner = TestPlanner.forForkCount(forkCount);

        return new BucketingAllocator(testPlanner, timingsProvider);
    }

    private final @NotNull TestPlanner testPlanner;
    private final @NotNull Supplier<Stream<TestTiming>> timedTestsProvider;

    private final @NotNull List<TestSource> sources = new ArrayList<>();
    private @Nullable TestPlan testPlan = null;

    private BucketingAllocator(@NotNull TestPlanner testPlanner, @NotNull Supplier<Stream<TestTiming>> timingsProvider) {
        this.testPlanner = testPlanner;
        this.timedTestsProvider = timingsProvider;
    }

    void addSource(@NotNull TestLister source, @NotNull Object testTask) {
        sources.add(TestSource.of(source.getAllTestsDiscovered(), testTask));
    }

    @NotNull List<String> getTestsForForkAndTestTask(int fork, @NotNull Object testTask) {
        if (testPlan == null) {
            throw new IllegalStateException("Test plan has not been generated");
        }

        return testPlan.getTestsForForkAndTask(fork, testTask);
    }

    void generateTestPlan() {
        Stream<TestBucket> matchedTests = TestBucketBuilder.using(timedTestsProvider.get())
                .getTestBuckets(sources);

        testPlan = testPlanner.createPlan(matchedTests);

        testPlan.getSummary().writeToConsole();
    }

}

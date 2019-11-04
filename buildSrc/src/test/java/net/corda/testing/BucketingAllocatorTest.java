package net.corda.testing;

import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;

public class BucketingAllocatorTest {

    @Test
    public void shouldAlwaysBucketTestsEvenIfNotInTimedFile() {
        Tests tests = new Tests();
        BucketingAllocator bucketingAllocator = new BucketingAllocator(1, () -> tests);

        Object task = new Object();
        bucketingAllocator.addSource(() -> Arrays.asList("SomeTestingClass", "AnotherTestingClass"), task);

        bucketingAllocator.generateTestPlan();
        List<String> testsForForkAndTestTask = bucketingAllocator.getTestsForForkAndTestTask(0, task);

        Assert.assertThat(testsForForkAndTestTask, IsIterableContainingInAnyOrder.containsInAnyOrder("SomeTestingClass", "AnotherTestingClass"));

        List<BucketingAllocator.TestsForForkContainer> forkContainers = bucketingAllocator.getForkContainers();
        Assert.assertEquals(1, forkContainers.size());
        // There aren't any known tests, so it will use the default instead.
        Assert.assertEquals(Tests.DEFAULT_MEAN_NANOS, tests.getMeanDurationForTests());
        Assert.assertEquals(2 * tests.getMeanDurationForTests(), forkContainers.get(0).getCurrentDuration().longValue());
    }

    @Test
    public void shouldAlwaysBucketTestsEvenIfNotInTimedFileAndUseMeanValue() {
        final Tests tests = new Tests();
        tests.addDuration("someRandomTestNameToForceMeanValue", 1_000_000_000);

        BucketingAllocator bucketingAllocator = new BucketingAllocator(1, () -> tests);

        Object task = new Object();
        List<String> testNames = Arrays.asList("SomeTestingClass", "AnotherTestingClass");

        bucketingAllocator.addSource(() -> testNames, task);

        bucketingAllocator.generateTestPlan();
        List<String> testsForForkAndTestTask = bucketingAllocator.getTestsForForkAndTestTask(0, task);

        Assert.assertThat(testsForForkAndTestTask, IsIterableContainingInAnyOrder.containsInAnyOrder(testNames.toArray()));

        List<BucketingAllocator.TestsForForkContainer> forkContainers = bucketingAllocator.getForkContainers();
        Assert.assertEquals(1, forkContainers.size());
        Assert.assertEquals(testNames.size() * tests.getMeanDurationForTests(), forkContainers.get(0).getCurrentDuration().longValue());
    }

    @Test
    public void shouldAllocateTestsAcrossForksEvenIfNoMatchingTestsFound() {
        Tests tests = new Tests();
        tests.addDuration("SomeTestingClass", 1_000_000_000);
        tests.addDuration("AnotherTestingClass", 2222);
        BucketingAllocator bucketingAllocator = new BucketingAllocator(2, () -> tests);

        Object task = new Object();
        bucketingAllocator.addSource(() -> Arrays.asList("SomeTestingClass", "AnotherTestingClass"), task);

        bucketingAllocator.generateTestPlan();
        List<String> testsForForkOneAndTestTask = bucketingAllocator.getTestsForForkAndTestTask(0, task);
        List<String> testsForForkTwoAndTestTask = bucketingAllocator.getTestsForForkAndTestTask(1, task);

        Assert.assertThat(testsForForkOneAndTestTask.size(), is(1));
        Assert.assertThat(testsForForkTwoAndTestTask.size(), is(1));

        List<String> allTests = Stream.of(testsForForkOneAndTestTask, testsForForkTwoAndTestTask).flatMap(Collection::stream).collect(Collectors.toList());

        Assert.assertThat(allTests, IsIterableContainingInAnyOrder.containsInAnyOrder("SomeTestingClass", "AnotherTestingClass"));
    }

    @Test
    public void shouldAllocateTestsAcrossForksEvenIfNoMatchingTestsFoundAndUseExisitingValues() {
        Tests tests = new Tests();
        tests.addDuration("SomeTestingClass", 1_000_000_000L);
        tests.addDuration("AnotherTestingClass", 3_000_000_000L);
        BucketingAllocator bucketingAllocator = new BucketingAllocator(2, () -> tests);

        Object task = new Object();
        bucketingAllocator.addSource(() -> Arrays.asList("YetAnotherTestingClass", "SomeTestingClass", "AnotherTestingClass"), task);

        bucketingAllocator.generateTestPlan();
        List<String> testsForForkOneAndTestTask = bucketingAllocator.getTestsForForkAndTestTask(0, task);
        List<String> testsForForkTwoAndTestTask = bucketingAllocator.getTestsForForkAndTestTask(1, task);

        Assert.assertThat(testsForForkOneAndTestTask.size(), is(1));
        Assert.assertThat(testsForForkTwoAndTestTask.size(), is(2));

        List<String> allTests = Stream.of(testsForForkOneAndTestTask, testsForForkTwoAndTestTask).flatMap(Collection::stream).collect(Collectors.toList());

        Assert.assertThat(allTests, IsIterableContainingInAnyOrder.containsInAnyOrder("YetAnotherTestingClass", "SomeTestingClass", "AnotherTestingClass"));

        List<BucketingAllocator.TestsForForkContainer> forkContainers = bucketingAllocator.getForkContainers();
        Assert.assertEquals(2, forkContainers.size());
        // Internally, we should have sorted the tests by decreasing size, so the largest would be added to the first bucket.
        Assert.assertEquals(TimeUnit.SECONDS.toNanos(3), forkContainers.get(0).getCurrentDuration().longValue());

        // At this point, the second bucket is empty.  We also know that the test average is 2s  (1+3)/2.
        // So we should put SomeTestingClass (1s) into this bucket, AND then put the 'unknown' test 'YetAnotherTestingClass'
        // into this bucket, using the mean duration = 2s, resulting in 3s.
        Assert.assertEquals(TimeUnit.SECONDS.toNanos(3), forkContainers.get(1).getCurrentDuration().longValue());
    }

    @Test
    public void testBucketAllocationForSeveralTestsDistributedByClassName() {
        Tests tests = new Tests();
        tests.addDuration("SmallTestingClass", 1_000_000_000L);
        tests.addDuration("LargeTestingClass", 3_000_000_000L);
        tests.addDuration("MediumTestingClass", 2_000_000_000L);
        // Gives a nice mean of 2s.
        Assert.assertEquals(TimeUnit.SECONDS.toNanos(2), tests.getMeanDurationForTests());

        BucketingAllocator bucketingAllocator = new BucketingAllocator(4, () -> tests);

        List<String> testNames = Arrays.asList(
                "EvenMoreTestingClass",
                "YetAnotherTestingClass",
                "AndYetAnotherTestingClass",
                "OhYesAnotherTestingClass",
                "MediumTestingClass",
                "SmallTestingClass",
                "LargeTestingClass");

        Object task = new Object();
        bucketingAllocator.addSource(() -> testNames, task);

        // does not preserve order of known tests and unknown tests....
        bucketingAllocator.generateTestPlan();

        List<String> testsForFork0 = bucketingAllocator.getTestsForForkAndTestTask(0, task);
        List<String> testsForFork1 = bucketingAllocator.getTestsForForkAndTestTask(1, task);
        List<String> testsForFork2 = bucketingAllocator.getTestsForForkAndTestTask(2, task);
        List<String> testsForFork3 = bucketingAllocator.getTestsForForkAndTestTask(3, task);

        Assert.assertThat(testsForFork0.size(), is(1));
        Assert.assertThat(testsForFork1.size(), is(2));
        Assert.assertThat(testsForFork2.size(), is(2));
        Assert.assertThat(testsForFork3.size(), is(2));

        // This must be true as it is the largest value.
        Assert.assertTrue(testsForFork0.contains("LargeTestingClass"));

        List<String> allTests = Stream.of(testsForFork0, testsForFork1, testsForFork2, testsForFork3)
                .flatMap(Collection::stream).collect(Collectors.toList());

        Assert.assertThat(allTests, IsIterableContainingInAnyOrder.containsInAnyOrder(testNames.toArray()));

        List<BucketingAllocator.TestsForForkContainer> forkContainers = bucketingAllocator.getForkContainers();
        Assert.assertEquals(4, forkContainers.size());

        long totalDuration = forkContainers.stream().mapToLong(c -> c.getCurrentDuration()).sum();
        Assert.assertEquals(tests.getMeanDurationForTests() * testNames.size(), totalDuration);

        Assert.assertEquals(TimeUnit.SECONDS.toNanos(3), forkContainers.get(0).getCurrentDuration().longValue());
        Assert.assertEquals(TimeUnit.SECONDS.toNanos(4), forkContainers.get(1).getCurrentDuration().longValue());
        Assert.assertEquals(TimeUnit.SECONDS.toNanos(4), forkContainers.get(2).getCurrentDuration().longValue());
        Assert.assertEquals(TimeUnit.SECONDS.toNanos(3), forkContainers.get(3).getCurrentDuration().longValue());
    }

    @Test
    public void durationToString() {
        Assert.assertEquals("1 mins", BucketingAllocator.getDuration(60_000_000_000L));
        Assert.assertEquals("4 secs", BucketingAllocator.getDuration(4_000_000_000L));
        Assert.assertEquals("400 ms", BucketingAllocator.getDuration(400_000_000L));
        Assert.assertEquals("400000 ns", BucketingAllocator.getDuration(400_000L));
    }
}
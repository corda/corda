package net.corda.testing;

import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;

public class BucketingAllocatorTest {

    @Test
    public void shouldAlwaysBucketTestsEvenIfNotInTimedFile() {

        BucketingAllocator bucketingAllocator = new BucketingAllocator(1, Collections::emptyList);

        Object task = new Object();
        bucketingAllocator.addSource(() -> Arrays.asList("SomeTestingClass", "AnotherTestingClass"), task);

        bucketingAllocator.generateTestPlan();
        List<String> testsForForkAndTestTask = bucketingAllocator.getTestsForForkAndTestTask(0, task);

        Assert.assertThat(testsForForkAndTestTask, IsIterableContainingInAnyOrder.containsInAnyOrder("SomeTestingClass", "AnotherTestingClass"));

    }


    @Test
    public void shouldAllocateTestsAcrossForksEvenIfNoMatchingTestsFound() {

        BucketingAllocator bucketingAllocator = new BucketingAllocator(2, Collections::emptyList);

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
}
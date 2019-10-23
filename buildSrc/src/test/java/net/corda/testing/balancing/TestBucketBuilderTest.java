package net.corda.testing.balancing;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

public class TestBucketBuilderTest {

    private final TestTiming firstRunOfA = TestTiming.of("a.1", 1.0);
    private final TestTiming secondRunOfA = TestTiming.of("a.2", 1.1);
    private final TestTiming firstRunOfC = TestTiming.of("c.1", 2.0);
    private final TestTiming secondRunOfC = TestTiming.of("c.2", 1.9);

    private final TestBucketBuilder unit = TestBucketBuilder.using(Stream.of(
            firstRunOfA, secondRunOfA, firstRunOfC, secondRunOfC
    ));

    @Test
    public void matchesTestsToTimingsByPrefix() {
        Object taskA = new Object();
        Object taskB = new Object();

        List<TestBucket> buckets = unit.getTestBuckets(Arrays.asList(
                TestSource.of(Stream.of("a"), taskA),
                TestSource.of(Stream.of("b"), taskB),
                TestSource.of(Stream.of("c"), taskB)
        )).collect(Collectors.toList());

        assertThat(buckets).containsExactly(
                // c first, because they're reverse-ordered by total runtime
                TestBucket.of(taskB, "c", asList(firstRunOfC, secondRunOfC)),
                TestBucket.of(taskA, "a", asList(firstRunOfA, secondRunOfA)),
                TestBucket.of(taskB, "b", emptyList())
        );
    }
}

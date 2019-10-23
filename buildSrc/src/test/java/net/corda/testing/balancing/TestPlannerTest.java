package net.corda.testing.balancing;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestPlannerTest {

    private final TestPlanner unit = TestPlanner.forForkCount(2);

    @Test
    public void assignsTestsToForks() {
        Object taskA = new Object();

        TestPlan plan = unit.createPlan(Stream.of(
                TestBucket.of(taskA, "a", Arrays.asList(TestTiming.of("a1", 2.0))),
                TestBucket.of(taskA, "b", Arrays.asList(TestTiming.of("b1", 1.0))),
                TestBucket.of(taskA, "c", Arrays.asList(TestTiming.of("c1", 1.0)))
        ));

        TestPlanSummary summary = plan.getSummary();

        assertThat(summary.getNumberOfForks()).isEqualTo(2);
        assertThat(summary.getTotalTestCount()).isEqualTo(3);

        assertThat(testsPlannedFor(2, Arrays.asList(taskA), plan)).containsExactlyInAnyOrder("a", "b", "c");

        plan.getSummary().writeToConsole();
    }

    private List<String> testsPlannedFor(int forkCount, List<Object> tasks, TestPlan plan) {
        return tasks.stream().flatMap(task ->
                IntStream.range(0, forkCount).mapToObj(fork ->
                        plan.getTestsForForkAndTask(fork, task)
                ).flatMap(List::stream)
        ).collect(Collectors.toList());
    }
}

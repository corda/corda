package net.corda.testing.balancing;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class TestPlan {

    static @NotNull TestPlan forForks(@NotNull List<TestsForForkContainer> forks) {
        TestPlanSummary summary = TestPlanSummary.forForks(forks);

        List<Map<Object, List<String>>> plansByForkAndTask = forks.stream()
                .map(TestsForForkContainer::getTestsByTask)
                .collect(Collectors.toList());

        return new TestPlan(plansByForkAndTask, summary);
    }

    private final @NotNull TestPlanSummary summary;
    private final @NotNull List<Map<Object, List<String>>> plansByForkAndTask;

    private TestPlan(@NotNull List<Map<Object, List<String>>> plansByForkAndTask, @NotNull TestPlanSummary summary) {
        this.plansByForkAndTask = plansByForkAndTask;
        this.summary = summary;
    }

    List<String> getTestsForForkAndTask(int fork, Object task) {
        return plansByForkAndTask.get(fork).get(task);
    }

    TestPlanSummary getSummary() {
        return summary;
    }
}

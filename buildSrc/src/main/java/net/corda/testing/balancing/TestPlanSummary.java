package net.corda.testing.balancing;

import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Useful for testing / outputting to console / other possible instrumentation
 */
final class TestPlanSummary {

    static TestPlanSummary forForks(List<TestsForForkContainer> forks) {
        return new TestPlanSummary(forks.stream()
                .map(TestsForForkContainer::getSummary)
                .collect(Collectors.toList()));
    }

    private final List<TestPlanForkSummary> forkSummaries;

    private TestPlanSummary(List<TestPlanForkSummary> forkSummaries) {
        this.forkSummaries = forkSummaries;
    }

    int getNumberOfForks() {
        return forkSummaries.size();
    }

    int getTotalTestCount() {
        return forkSummaries.stream().mapToInt(TestPlanForkSummary::getNumberOfTests).sum();
    }

    void writeToConsole() {
        writeTo(new PrintWriter(System.out));
    }

    void writeTo(PrintWriter writer) {
        forkSummaries.forEach(summary -> summary.writeTo(writer));
        writer.flush();
    }
}

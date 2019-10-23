package net.corda.testing.balancing;

import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

final class TestPlanForkSummary {

    static TestPlanForkSummary forFork(int forkIdx, double duration, List<TestBucket> testsForFork) {
        return new TestPlanForkSummary(
                forkIdx,
                duration,
                testsForFork.size(),
                testsForFork.stream().map(TestPlanTestSummary::forBucket).collect(Collectors.toList())
        );
    }

    private final int forkIdx;
    private final double duration;
    private final int numberOfTests;
    private final List<TestPlanTestSummary> testsToRun;

    private TestPlanForkSummary(int forkIdx, double duration, int numberOfTests, List<TestPlanTestSummary> testsToRun) {
        this.forkIdx = forkIdx;
        this.duration = duration;
        this.numberOfTests = numberOfTests;
        this.testsToRun = testsToRun;
    }

    int getNumberOfTests() {
        return numberOfTests;
    }

    void writeTo(PrintWriter writer) {
        writer.print("####### TEST PLAN SUMMARY ( ");
        writer.print(forkIdx);
        writer.println(" ) #######");

        writer.print("Duration: ");
        writer.println(duration);

        writer.print("Number of tests: ");
        writer.println(numberOfTests);

        writer.println("Tests to Run:");
        testsToRun.forEach(testToRun -> testToRun.writeTo(writer));
    }
}

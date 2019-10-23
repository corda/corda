package net.corda.testing.balancing;

import java.io.PrintWriter;
import java.util.List;

final class TestPlanTestSummary {

    static TestPlanTestSummary forBucket(TestBucket testBucket) {
        return new TestPlanTestSummary(
                testBucket.getTestName(),
                testBucket.getFoundTests()
        );
    }

    private final String testName;
    private final List<TestTiming> foundTests;

    private TestPlanTestSummary(String testName, List<TestTiming> foundTests) {
        this.testName = testName;
        this.foundTests = foundTests;
    }

    void writeTo(PrintWriter writer) {
        writer.println(testName);

        foundTests.forEach(foundTest -> {
            writer.print("\t");
            writer.print(foundTest.getName());
            writer.print(", ");
            writer.println(foundTest.getRunTime());
        });
    }
}

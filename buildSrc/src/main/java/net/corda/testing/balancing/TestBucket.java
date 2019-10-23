package net.corda.testing.balancing;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

class TestBucket {

    static @NotNull TestBucket of(@NotNull Object testTask, @NotNull String testName, @NotNull List<TestTiming> testTimings) {
        return new TestBucket(
                testTask,
                testName,
                testTimings,
                Math.max(
                        testTimings.stream()
                            .mapToDouble(tp -> Math.max(tp.getRunTime(), 1))
                            .sum(),
                        1));
    }

    private final @NotNull Object testTask;
    private final @NotNull String testName;
    private final @NotNull List<TestTiming> foundTests;
    private final double duration;

    private TestBucket(@NotNull Object testTask, @NotNull String testName, @NotNull List<TestTiming> foundTests, double duration) {
        this.testTask = testTask;
        this.testName = testName;
        this.foundTests = foundTests;
        this.duration = duration;
    }

    @NotNull Object getTestTask() {
        return testTask;
    }

    @NotNull String getTestName() {
        return testName;
    }

    @NotNull List<TestTiming> getFoundTests() {
        return foundTests;
    }

    double getDuration() {
        return duration;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TestBucket && equalsTestBucket((TestBucket) o);
    }

    private boolean equalsTestBucket(@NotNull TestBucket o) {
        return Objects.equals(o.testTask, testTask) &&
                Objects.equals(o.testName, testName) &&
                Objects.equals(o.foundTests, foundTests) &&
                Objects.equals(o.duration, duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testTask, testName, foundTests, duration);
    }

    @Override
    public String toString() {
        return "TestBucket{" +
                "testTask=" + testTask +
                ", nameWithAsterix='" + testName + '\'' +
                ", foundTests=" + foundTests +
                ", duration=" + duration +
                '}';
    }
}

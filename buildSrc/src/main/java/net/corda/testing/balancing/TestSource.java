package net.corda.testing.balancing;

import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

final class TestSource {

    static @NotNull TestSource of(@NotNull Stream<String> testNames, @NotNull Object testTask) {
        return new TestSource(testNames, testTask);
    }

    private final Stream<String> testNames;
    private final Object testTask;

    private TestSource(@NotNull Stream<String> testNames, @NotNull Object testTask) {
        this.testNames = testNames;
        this.testTask = testTask;
    }

    Stream<DiscoveredTest> getDiscoveredTests() {
        return testNames.map(test -> new DiscoveredTest(test, testTask));
    }
}

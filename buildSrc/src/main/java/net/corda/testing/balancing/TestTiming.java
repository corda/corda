package net.corda.testing.balancing;

import org.jetbrains.annotations.NotNull;

final class TestTiming {

    static @NotNull TestTiming of(@NotNull String name, double runTime) {
        return new TestTiming(name, runTime);
    }

    private final String name;
    private final double runTime;

    private TestTiming(@NotNull  String name, double runTime) {
        this.name = name;
        this.runTime = runTime;
    }

    @NotNull String getName() {
        return name;
    }

    double getRunTime() {
        return runTime;
    }
}

package net.corda.testing.balancing;

import org.jetbrains.annotations.NotNull;

final class DiscoveredTest {

    private final @NotNull String name;
    private final @NotNull Object task;

    DiscoveredTest(@NotNull String name, @NotNull Object task) {
        this.name = name;
        this.task = task;
    }

    @NotNull String getName() {
        return name;
    }
    @NotNull Object getTask() {
        return task;
    }
}

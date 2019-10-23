package net.corda.testing.balancing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class TestsForForkContainer {

    private double runningDuration = 0.0;
    private final int forkIdx;

    private final List<TestBucket> testsForFork = new ArrayList<>();


    TestsForForkContainer(int forkIdx) {
        this.forkIdx = forkIdx;
    }

    void addBucket(TestBucket tb) {
        this.testsForFork.add(tb);
        this.runningDuration = runningDuration + tb.getDuration();
    }

    double getCurrentDuration() {
        return runningDuration;
    }

    Map<Object, List<String>> getTestsByTask() {
        return testsForFork.stream()
                .collect(
                        Collectors.groupingBy(TestBucket::getTestTask,
                        Collectors.mapping(TestBucket::getTestName, Collectors.toList())));
    }

    TestPlanForkSummary getSummary() {
        return TestPlanForkSummary.forFork(
                forkIdx,
                runningDuration,
                testsForFork);
    }

    @Override
    public String toString() {
        return "TestsForForkContainer{" +
                "runningDuration=" + runningDuration +
                ", forkIdx=" + forkIdx +
                ", testsForFork=" + testsForFork +
                '}';
    }
}

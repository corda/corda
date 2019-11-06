package net.corda.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

class ListShufflerAndAllocator {

    private final List<String> tests;

    public ListShufflerAndAllocator(List<String> tests) {
        this.tests = new ArrayList<>(tests);
    }

    public List<String> getTestsForFork(int fork, int forks, Integer seed) {
        final Random shuffler = new Random(seed);
        final List<String> copy = new ArrayList<>(tests);
        while (copy.size() < forks) {
            //pad the list
            copy.add(null);
        }
        Collections.shuffle(copy, shuffler);
        final int numberOfTestsPerFork = Math.max((copy.size() / forks), 1);
        final int consumedTests = numberOfTestsPerFork * forks;
        final int ourStartIdx = numberOfTestsPerFork * fork;
        final int ourEndIdx = ourStartIdx + numberOfTestsPerFork;
        final int ourSupplementaryIdx = consumedTests + fork;
        final ArrayList<String> toReturn = new ArrayList<>(copy.subList(ourStartIdx, ourEndIdx));
        if (ourSupplementaryIdx < copy.size()) {
            toReturn.add(copy.get(ourSupplementaryIdx));
        }
        return toReturn.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }
}
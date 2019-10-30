//package net.corda.testing;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Objects;
//import java.util.Random;
//import java.util.stream.Collectors;
//
//class ListShufflerAndAllocator {
//
//    private final List<String> tests;
//
//    public ListShufflerAndAllocator(List<String> tests) {
//        this.tests = new ArrayList<>(tests);
//    }
//
//    public List<String> getTestsForFork(int fork, int forks, Integer seed) {
//        Random shuffler = new Random(seed);
//        List<String> copy = new ArrayList<>(tests);
//        while (copy.size() < forks) {
//            //pad the list
//            copy.add(null);
//        }
//        Collections.shuffle(copy, shuffler);
//        int numberOfTestsPerFork = Math.max((copy.size() / forks), 1);
//        int consumedTests = numberOfTestsPerFork * forks;
//        int ourStartIdx = numberOfTestsPerFork * fork;
//        int ourEndIdx = ourStartIdx + numberOfTestsPerFork;
//        int ourSupplementaryIdx = consumedTests + fork;
//        ArrayList<String> toReturn = new ArrayList<>(copy.subList(ourStartIdx, ourEndIdx));
//        if (ourSupplementaryIdx < copy.size()) {
//            toReturn.add(copy.get(ourSupplementaryIdx));
//        }
//        return toReturn.stream().filter(Objects::nonNull).collect(Collectors.toList());
//    }
//}
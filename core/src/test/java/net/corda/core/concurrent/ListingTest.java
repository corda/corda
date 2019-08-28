package net.corda.core.concurrent;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ListingTest {

    class ListShufflerAndAllocator {

        private final java.util.List<String> tests;

        public ListShufflerAndAllocator(List<String> tests) {
            this.tests = new ArrayList<>(tests);
        }

        List<String> getTestsForFork(int fork, int forks, Integer seed) {
            Random shuffler = new Random(seed);
            List<java.lang.String> copy = new ArrayList<>(tests);
            while (copy.size() < forks) {
                //pad the list
                copy.add(null);
            }
            Collections.shuffle(copy, shuffler);
            int numberOfTestsPerFork = Math.max(copy.size() / forks, 1);
            int consumedTests = numberOfTestsPerFork * forks;
            int ourStartIdx = numberOfTestsPerFork * fork;
            int ourEndIdx = ourStartIdx + numberOfTestsPerFork;
            int ourSupplementaryIdx = consumedTests + fork;
            ArrayList<String> toReturn = new ArrayList<>(copy.subList(ourStartIdx, ourEndIdx));
            if (ourSupplementaryIdx < copy.size()) {
                toReturn.add(copy.get(ourSupplementaryIdx));
            }
            return toReturn.stream().filter(Objects::nonNull).collect(Collectors.toList());
        }
    }


    @Test
    public void test() {

        for (int numberOfTests = 1; numberOfTests < 100; numberOfTests++) {
            for (int numberOfForks = 1; numberOfForks < 100; numberOfForks++) {


                List<String> tests = IntStream.range(0, numberOfTests).mapToObj(z -> "Test.method" + z).collect(Collectors.toList());
                ListShufflerAndAllocator testLister = new ListShufflerAndAllocator(tests);

                int forks = numberOfForks;

                List<String> listOfLists = new ArrayList<>();
                for (int k = 0; k < forks; k++) {
                    listOfLists.addAll(testLister.getTestsForFork(k, forks, 0));
                }

                Assert.assertThat(listOfLists.size(), CoreMatchers.is(tests.size()));
                Assert.assertThat(new HashSet<>(listOfLists).size(), CoreMatchers.is(tests.size()));
            }
        }



    }
}

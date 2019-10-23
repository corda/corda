package net.corda.testing.listing;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ListTestsTest {

    @Test
    public void shouldAllocateTests() {

        for (int numberOfTests = 0; numberOfTests < 100; numberOfTests++) {
            for (int numberOfForks = 1; numberOfForks < 100; numberOfForks++) {
                List<String> tests = IntStream.range(0, numberOfTests).mapToObj( z -> "Test.method" + z ).collect(Collectors.toList());
                ListShufflerAndAllocator testLister = new ListShufflerAndAllocator(tests);

                List<String> listOfLists = new ArrayList<>();
                for (int fork = 0; fork < numberOfForks; fork++) {
                    listOfLists.addAll(testLister.getTestsForFork(fork, numberOfForks, 0));
                }

                assertThat(listOfLists).hasSize(tests.size());
                assertThat(listOfLists).containsAll(tests);
            }
        }

    }

}

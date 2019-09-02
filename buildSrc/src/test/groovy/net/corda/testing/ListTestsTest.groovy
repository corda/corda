package net.corda.testing

import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test

import java.util.stream.Collectors
import java.util.stream.IntStream

import static org.hamcrest.core.Is.is
import static org.hamcrest.core.IsEqual.equalTo

class ListTestsTest {

    @Test
    void shouldAllocateTests() {

        for (int numberOfTests = 1; numberOfTests < 100; numberOfTests++) {
            for (int numberOfForks = 1; numberOfForks < 100; numberOfForks++) {


                List<String> tests = IntStream.range(0, numberOfTests).collect { z -> "Test.method" + z }
                ListShufflerAndAllocator testLister = new ListShufflerAndAllocator(tests);

                List<String> listOfLists = new ArrayList<>();
                for (int fork = 0; fork < numberOfForks; fork++) {
                    listOfLists.addAll(testLister.getTestsForFork(fork, numberOfForks, 0));
                }

                Assert.assertThat(listOfLists.size(), CoreMatchers.is(tests.size()));
                Assert.assertThat(new HashSet<>(listOfLists).size(), CoreMatchers.is(tests.size()));
                Assert.assertThat(listOfLists.stream().sorted().collect(Collectors.toList()), is(equalTo(tests.stream().sorted().collect(Collectors.toList()))));
            }
        }

    }

}

package com.stefano.testing

import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test

import java.util.stream.IntStream

class ListTestsTest {

    @Test
    void shouldAllocateTests() {

        def tests = IntStream.range(0, 39).mapToObj { i -> "Test.method$i" }.collect { it -> it }
        ListShufflerAndAllocator testLister = new ListShufflerAndAllocator(tests)


        def forks = 17

        List<String> listOfLists = new ArrayList<>()
        for (int i = 0; i < forks; i++) {
            listOfLists.addAll(testLister.getTestsForFork(i, forks, 0))
        }

//        Assert.assertThat(listOfLists.size(), CoreMatchers.is(tests.size()))
//        Assert.assertThat(listOfLists.toSet().size(), CoreMatchers.is(tests.size()))

    }

}

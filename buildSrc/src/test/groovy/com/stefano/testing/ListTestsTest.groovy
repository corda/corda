package com.stefano.testing

class ListTestsTest {

    public static void main(String[] args) {
        def lt = new ListTests()

        lt.allTests = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8")
        println lt.getTestsForFork(0, 4, 0)
        println lt.getTestsForFork(1, 4, 0)
        println lt.getTestsForFork(2, 4, 0)
        println lt.getTestsForFork(3, 4, 0)
    }

}

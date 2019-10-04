package net.corda.testing

import org.junit.Assert
import org.junit.Test

import java.util.stream.IntStream

class PartitionTestsByDurationTests {

    public static final BigDecimal delta = 0.0001

    @Test
    void partitionByDurationSortedCorrectly() {
        def allTests = ["one", "two", "three", "four"]
        def allTestsByDuration = [
                "three": 3.0,
                "two"  : 2.0,
                "one"  : 1.0,
                "five" : 5.0,
                "four" : 4.0
        ]

        def partitioner = new PartitionTestsByDuration(1, allTests, allTestsByDuration)

        Assert.assertFalse(partitioner.allTestsSortedByDuration.empty)
        Assert.assertEquals(partitioner.allTestsSortedByDuration.first().duration, 4.0, delta)
        Assert.assertEquals(partitioner.allTestsSortedByDuration.last().duration, 1.0, delta)
    }

    @Test
    void average() {
        def allTests = ["one", "two", "three", "four"]
        def allTestsByDuration = [
                three: 3.0,
                two  : 2.0,
                one  : 1.0,
                five : 5.0,
                four : 4.0
        ]

        def partitioner = new PartitionTestsByDuration(1, allTests, allTestsByDuration)
        Assert.assertEquals(partitioner.defaultDuration.doubleValue(), 3.0, delta)
    }

    @Test
    void durationOf() {
        def allTests = ["one", "two", "three", "four"]
        def allTestsByDuration = [
                three: 3.0,
                two  : 2.0,
                one  : 1.0,
        ]
        def partitioner = new PartitionTestsByDuration(1, allTests, allTestsByDuration)
        Assert.assertEquals(partitioner.getDuration("two"), 2.0, delta)
        Assert.assertEquals(partitioner.getDuration("four"), 2.0, delta)
    }

    @Test
    void testPartitioningByDurationWithKnownTests() {
        int partitionCount = 10
        // 1000 tests with duration 1 to 1000.
        int testCount = 1000

        def tests = IntStream.range(0, testCount).collect { z -> "Test.method" + z }

        //  Add one to the duration as we want non-zero
        def testsWithDuration = new HashMap<String, Double>()
        IntStream.range(0, testCount).forEach { i -> testsWithDuration.put("Test.method" + i, (double) (i + 1)) }

        def partitioner = new PartitionTestsByDuration(partitionCount, tests, testsWithDuration)
        def otherPartitioner = new PartitionTestsByDuration(partitionCount, tests, testsWithDuration)

        def testsForPartition = partitioner.getAllTestsForPartition(0)
        def testsForOtherPartition = otherPartitioner.getAllTestsForPartition(0)

        Assert.assertEquals(testsForPartition, testsForOtherPartition)

        //  Optimal is always going to be sumOf(allDurations) / numberOfParitions, e.g.
        //  1000 tests with duration 1 to 1000 inclusive, == (1+1000) * 1000/2 = 500500
        //  500500 / 10 == 50050 per bucket for 10 buckets

        //  wikipedia says that the greedy algorithm approximately upto one third larger than
        //  the optimal solution, i.e. 30m optimal, but greedy can produce up to a 40m bucket.
        //  This is true for well distributed tests.  Clearly if we have [10000, 1, 2, 3, 4, 5] etc.
        //  We will have one partition of 10000, and the rest will be much smaller.

        double optimalDuration = (double) ((1 + testCount) * (testCount / 2)) / (double) partitionCount
        double tolerance = 0.3 * optimalDuration

        Assert.assertEquals(partitioner.getDuration(0), optimalDuration, tolerance)
    }

    @Test
    void testParitioningIsDeterministic() {
        int partitionCount = 3
        int testCount = 300

        def tests = IntStream.range(0, testCount).collect { z -> "Test.method" + z }

        //  Add one to the duration as we want non-zero
        def testsWithDuration = new HashMap<String, Double>()
        IntStream.range(0, testCount).forEach { testsWithDuration.put("Test.method" + it, (double) (it + 1)) }

        def partitioner = new PartitionTestsByDuration(partitionCount, tests, testsWithDuration)
        def otherPartitioner = new PartitionTestsByDuration(partitionCount, tests, testsWithDuration)

        def testsForPartition = partitioner.getAllTestsForPartition(0)
        def testsForOtherPartition = otherPartitioner.getAllTestsForPartition(0)

        Assert.assertEquals(testsForPartition, testsForOtherPartition)

        //Assert.fail("FAILED ON PURPOSE")
    }

    @Test
    void testPartitionDistributionForSimpleCase() {
        def allTests = ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"]
        def allTestsByDuration = [
                a: 1.0,
                b: 2.0,
                c: 3.0,
                d: 4.0,
                e: 5.0,
                f: 6.0,
                g: 7.0,
                h: 8.0,
                i: 9.0,
                j: 10.0,
                k: 11.0,
                l: 12.0,
                m: 13.0,
        ]

        def partitioner = new PartitionTestsByDuration(3, allTests, allTestsByDuration)

        Assert.assertEquals(partitioner.getAllTestsForPartition(0), ['k', 'f', 'e'])
        Assert.assertEquals(partitioner.getDuration(0), 22.0, delta)
        Assert.assertEquals(partitioner.getAllTestsForPartition(1), ['j', 'g', 'd', 'a'])
        Assert.assertEquals(partitioner.getDuration(1), 22.0, delta)
        Assert.assertEquals(partitioner.getAllTestsForPartition(2), ['i', 'h', 'c', 'b'])
        Assert.assertEquals(partitioner.getDuration(2), 22.0, delta)
    }


    @Test
    void testPartitionDistributionForOutliers() {
        def allTests = ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"]
        def allTestsByDuration = [
                a: 1.0,
                b: 2.0,
                c: 3.0,
                d: 4.0,
                e: 5.0,
                f: 6.0,
                g: 7.0,
                h: 8.0,
                i: 9.0,
                j: 89.0,
                k: 99.0,
                l: 12.0,
                m: 13.0,
        ]

        def partitioner = new PartitionTestsByDuration(3, allTests, allTestsByDuration)

        Assert.assertEquals(partitioner.getAllTestsForPartition(0), ['k'])
        Assert.assertEquals(partitioner.getDuration(0), 99.0, delta)
        Assert.assertEquals(partitioner.getAllTestsForPartition(1), ['j'])
        Assert.assertEquals(partitioner.getDuration(1), 89.0, delta)
        Assert.assertEquals(partitioner.getAllTestsForPartition(2), ['i', 'h', 'g', 'f', 'e', 'd', 'c', 'b', 'a'])
        Assert.assertEquals(partitioner.getDuration(2), 45.0, delta)
    }

    @Test
    void testZeroDurationTestsAreGivenPenaltyValue() {
        def allTestsByDuration = [
                a: 0.0,
                b: 0.0,
                c: 0.0,
        ]
        def allTests = ["a", "b", "c"]
        def partitioner = new PartitionTestsByDuration(3, allTests, allTestsByDuration)
        Assert.assertEquals(partitioner.getAllTestsForPartition(0), ['a'])
        Assert.assertEquals(partitioner.getDuration(0), PartitionTestsByDuration.PENALTY, delta)
        Assert.assertEquals(partitioner.getAllTestsForPartition(1), ['b'])
        Assert.assertEquals(partitioner.getDuration(1), PartitionTestsByDuration.PENALTY, delta)
        Assert.assertEquals(partitioner.getAllTestsForPartition(2), ['c'])
        Assert.assertEquals(partitioner.getDuration(2), PartitionTestsByDuration.PENALTY, delta)
    }

    @Test
    void canReadTeamCityCsv() {
        String snippet = "Order#,Test Name,Status,Duration(ms)\n" +
                "1,net.corda.common.configuration.parsing.internal.PropertyTest.optional_absent_value_of_list_type,OK,264\n" +
                "2,net.corda.common.configuration.parsing.internal.PropertyTest.absent_value_of_list_type_with_whole_list_mapping,OK,8\n"

        def reader = new StringReader(snippet)
        def tests = PartitionTestsByDuration.fromTeamCityCsv(reader)

        Assert.assertTrue(!tests.empty)
        Assert.assertEquals(tests.size(), 2)

        def allTests = new ArrayList(tests.keySet())

        int partitions = 10
        def partitioner = new PartitionTestsByDuration(partitions, allTests, tests)

        Assert.assertEquals(partitioner.getSize(), partitions)
        Assert.assertEquals(partitioner.getAllTestsForPartition(0).size(), 1)
        Assert.assertEquals(partitioner.getAllTestsForPartition(1).size(), 1)
        Assert.assertEquals(partitioner.getAllTestsForPartition(2).size(), 0)
    }

    @Test
    void failsWithBadCsv() {
        String snippet = "random nonsense that is not a csv file\nmore than one line\n"

        def reader = new StringReader(snippet)
        def tests = PartitionTestsByDuration.fromTeamCityCsv(reader)

        Assert.assertTrue(tests.isEmpty())
    }

    /**
     * Check that the distribution is 'unique', i.e. each test is only ever emitted once from a call to
     * getAllTestsForPartition()
     */
    @Test
    void ensureDistributionIsUnique() {
        int partitionCount = 5
        int testCount = 300

        def tests = IntStream.range(0, testCount).collect { z -> "Test.method" + z }

        //  Add one to the duration as we want non-zero
        def testsWithDuration = new HashMap<String, Double>()
        IntStream.range(0, testCount).forEach { testsWithDuration.put("Test.method" + it, (double) (it + 1)) }

        def partitioner = new PartitionTestsByDuration(partitionCount, tests, testsWithDuration)

        def expectedTests = testsWithDuration.keySet()
        def actualTests = new ArrayList<String>()

        List<Set<String>> partitions = new ArrayList<>()

        for (int i = 0; i < partitionCount; ++i) {
            def partition = partitioner.getAllTestsForPartition(i)
            actualTests.addAll(partition)
            partitions.add(new HashSet<String>(partition))
        }

        for (int i = 0 ; i < partitionCount ; ++i) {
            for (int j = 0 ; j < partitionCount ; ++j) {
                if (i == j) continue

                def copy = new HashSet<String>(partitions.get(j))
                copy.removeAll(partitions.get(i))

                // i.e. no elements have been removed from j as it is unique from i.
                Assert.assertEquals("Two partitions should not contain the same test ${i} and ${j}",
                        copy.size(), partitions.get(j).size()
                )
            }
        }

        def setOfActualTests = new HashSet<String>(actualTests)
        Assert.assertTrue("Duplicate tests on forks", actualTests.size() == setOfActualTests.size())

        Assert.assertEquals("Expected all tests to be distributed", expectedTests, setOfActualTests)
    }

    @Test
    void ensureRandomDistributionIsUnique() {
        // reusable seed for debugging so numbers are always the same
        Random r = new Random(0x1abe11ed)
        int idx = 0
        int testCount = 10000
        int partitionCount = 11

        def testsWithDuration = new HashMap<String, Double>()

        r.ints(testCount).forEach({
            testsWithDuration.put("Test.method" + idx, (double) (it + 1))
            ++idx
        })

        def tests = testsWithDuration.keySet()
        def partitioner = new PartitionTestsByDuration(partitionCount, tests, testsWithDuration)

        def expectedTests = testsWithDuration.keySet()
        def actualTests = new ArrayList<String>()

        for (int i = 0; i < partitionCount; ++i) {
            actualTests.addAll(partitioner.getAllTestsForPartition(i))
        }

        def setOfActualTests = new HashSet<String>(actualTests)
        Assert.assertTrue("Duplicate tests on forks", actualTests.size() == setOfActualTests.size())

        // Could be massive output to screen if fails (value of testCount).
        // Note, both expected and equals would be displayed.
        Assert.assertEquals("Expected all tests to be distributed", expectedTests, setOfActualTests)
    }
}

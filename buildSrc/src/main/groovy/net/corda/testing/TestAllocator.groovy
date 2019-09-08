package net.corda.testing

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

class TestAllocator extends DefaultTask {

    List<ListTests> testListers = new ArrayList<>()
    Map<Integer, Map<Test, List<String>>> shardedMap = new HashMap<>()

    void testLists(ListTests... listers) {
        testLists(listers.toList())
    }

    void testLists(List<ListTests> listers) {
        listers.forEach { it ->
            testListers.add(it)
            dependsOn(it)
        }
    }

    @TaskAction
    void processLists() {
        def numberOfForks = DistributedTesting.getPropertyAsInt(project, DistributedTesting.FORKS_PROPERTY)
        List<Tuple2<Test, String>> allTests = new ArrayList<>()
        testListers.forEach {
            def task = it.testTask
            def allTestsForTask = new ArrayList(it.allTests)
            allTests.addAll(allTestsForTask.collect { new Tuple2<>(task, it) })
        }

        //OK now we have all tests for all tasks - we need to ensure that each "fork" gets a similar number of tests
        //TODO join tests with build information and use a weighted measure of time to determine balance
        Collections.shuffle(allTests)
        int forkIdx = 0
        allTests.forEach {
            Map<Test, List<String>> shardMap = shardedMap.computeIfAbsent(forkIdx, { ignored -> new HashMap<>() })
            List<String> testsForTaskForFork = shardMap.computeIfAbsent(it.first, { ignored -> new ArrayList<>() })
            testsForTaskForFork.add(it.second)
            forkIdx = (forkIdx + 1) % numberOfForks
        }
        println ""
    }

    def getTestsForTaskAndFork(Test task, int fork) {
        return shardedMap.getOrDefault(fork, new HashMap<Test, List<String>>()).getOrDefault(task, new ArrayList<String>())
    }

}

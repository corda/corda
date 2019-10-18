package net.corda.testing;

//Why Java?! because sometimes types are useful.

import groovy.lang.Tuple2;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BucketingAllocator {
    private List<Tuple2<TestLister, Object>> sources = new ArrayList<>();
    private final List<TestsForForkContainer> forkContainers;
    private final Supplier<List<Tuple2<String, Double>>> timedTestsProvider;


    public BucketingAllocator(Integer forkCount, Supplier<List<Tuple2<String, Double>>> timedTestsProvider) {
        this.forkContainers = IntStream.range(0, forkCount).mapToObj(TestsForForkContainer::new).collect(Collectors.toList());
        this.timedTestsProvider = timedTestsProvider;
    }

    public void addSource(TestLister source, Object testTask) {
        sources.add(new Tuple2<>(source, testTask));
    }

    public List<String> getTestsForForkAndTestTask(Integer fork, Object testTask) {
        return forkContainers.get(fork).getTestsForTask(testTask);
    }

    @TaskAction
    public void generateTestPlan() {
        List<Tuple2<String, Double>> allTestsFromFile = timedTestsProvider.get();
        List<Tuple2<String, Object>> allDiscoveredTests = getTestsOnClasspathOfTestingTasks();
        List<TestBucket> matchedTests = matchClasspathTestsToFile(allTestsFromFile, allDiscoveredTests);

        //use greedy algo - for each testbucket find the currently smallest container and add to it
        allocateTestsToForks(matchedTests);
        forkContainers.forEach(TestsForForkContainer::freeze);

        printSummary();
    }

    private void printSummary() {
        forkContainers.forEach(container -> {
            System.out.println("####### TEST PLAN SUMMARY ( " + container.forkIdx + " ) #######");
            System.out.println("Duration: " + container.getCurrentDuration());
            System.out.println("Number of tests: " + container.testsForFork.stream().mapToInt(b -> b.foundTests.size()).sum());
            System.out.println("Tests to Run: ");
            container.testsForFork.forEach(tb -> {
                System.out.println(tb.testName);
                tb.foundTests.forEach(ft -> System.out.println("\t" + ft.getFirst() + ", " + ft.getSecond()));
            });
        });
    }

    private void allocateTestsToForks(@NotNull List<TestBucket> matchedTests) {
        matchedTests.forEach(matchedTestBucket -> {
            TestsForForkContainer smallestContainer = Collections.min(forkContainers, Comparator.comparing(TestsForForkContainer::getCurrentDuration));
            smallestContainer.addBucket(matchedTestBucket);
        });
    }

    private List<TestBucket> matchClasspathTestsToFile(List<Tuple2<String, Double>> allTestsFromFile, @NotNull List<Tuple2<String, Object>> allDiscoveredTests) {
        return allDiscoveredTests.stream().map(tuple -> {
            String testName = tuple.getFirst();
            Object task = tuple.getSecond();
            //2DO [can this filtering algorithm be improved - the test names are sorted, it should be possible to do something using binary search]
            List<Tuple2<String, Double>> matchingTests = allTestsFromFile.stream()
                    .filter(testFromCSV -> testFromCSV.getFirst().startsWith(testName))
                    .collect(Collectors.toList());

            return new TestBucket(task, testName, matchingTests);
        }).sorted(Comparator.comparing(TestBucket::getDuration).reversed()).collect(Collectors.toList());
    }

    private List<Tuple2<String, Object>> getTestsOnClasspathOfTestingTasks() {
        return sources.stream().map(source -> {
            TestLister lister = source.getFirst();
            Object testTask = source.getSecond();
            return lister.getAllTestsDiscovered().stream().map(test -> new Tuple2<>(test, testTask)).collect(Collectors.toList());
        }).flatMap(Collection::stream).sorted(Comparator.comparing(Tuple2::getFirst)).collect(Collectors.toList());
    }

    public static class TestBucket {
        final Object testTask;
        final String testName;
        final List<Tuple2<String, Double>> foundTests;
        final Double duration;

        public TestBucket(Object testTask, String testName, List<Tuple2<String, Double>> foundTests) {
            this.testTask = testTask;
            this.testName = testName;
            this.foundTests = foundTests;
            duration = Math.max(foundTests.stream().mapToDouble(tp -> Math.max(tp.getSecond(), 1)).sum(), 1);
        }

        public Double getDuration() {
            return duration;
        }

        @Override
        public String toString() {
            return "TestBucket{" +
                    "testTask=" + testTask +
                    ", nameWithAsterix='" + testName + '\'' +
                    ", foundTests=" + foundTests +
                    ", duration=" + duration +
                    '}';
        }
    }

    public static class TestsForForkContainer {
        private Double runningDuration = 0.0;
        private final Integer forkIdx;

        private final List<TestBucket> testsForFork = Collections.synchronizedList(new ArrayList<>());
        private final Map<Object, List<TestBucket>> frozenTests = new HashMap<>();

        public TestsForForkContainer(Integer forkIdx) {
            this.forkIdx = forkIdx;
        }

        public void addBucket(TestBucket tb) {
            this.testsForFork.add(tb);
            this.runningDuration = runningDuration + tb.duration;
        }

        public Double getCurrentDuration() {
            return runningDuration;
        }

        public void freeze() {
            testsForFork.forEach(tb -> {
                frozenTests.computeIfAbsent(tb.testTask, i -> new ArrayList<>()).add(tb);
            });
        }

        public List<String> getTestsForTask(Object task) {
            return frozenTests.getOrDefault(task, Collections.emptyList()).stream().map(it -> it.testName).collect(Collectors.toList());
        }

        public List<TestBucket> getBucketsForFork() {
            return new ArrayList<>(testsForFork);
        }

        @Override
        public String toString() {
            return "TestsForForkContainer{" +
                    "runningDuration=" + runningDuration +
                    ", forkIdx=" + forkIdx +
                    ", testsForFork=" + testsForFork +
                    ", frozenTests=" + frozenTests +
                    '}';
        }
    }


}

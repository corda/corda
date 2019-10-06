package net.corda.testing;

//Why Java?! because sometimes types are useful.


import groovy.lang.Tuple2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BucketingAllocator extends DefaultTask {

    List<Tuple2<ListTests, Test>> sources = new ArrayList<>();
    private final Integer forkCount;
    private final List<TestsForForkContainer> forkContainers;

    @Inject
    public BucketingAllocator(Integer forkCount) {
        this.forkCount = forkCount;
        this.forkContainers = IntStream.range(0, forkCount).mapToObj(TestsForForkContainer::new).collect(Collectors.toList());
    }

    public void addSource(ListTests source, Test testTask) {
        sources.add(new Tuple2<>(source, testTask));
        this.dependsOn(source);
    }

    public List<String> getTestsForForkAndTestTask(Integer fork, Test testTask) {
        return forkContainers.get(fork).getTestsForTask(testTask);
    }

    @TaskAction
    public void generateTestPlan() throws Exception {
        FileReader csvSource = new FileReader(new File(this.getProject().getRootDir(), "testing/test-times.csv"));
        List<Tuple2<String, Double>> allTestsFromCSV = fromCSV(csvSource);
        List<Tuple2<String, Test>> allDiscoveredTests = sources.stream().map(source -> {
            ListTests lister = source.getFirst();
            Test testTask = source.getSecond();
            return lister.getAllTestsDiscovered().stream().map(test -> new Tuple2<>(test, testTask)).collect(Collectors.toList());
        }).flatMap(l -> l.stream()).collect(Collectors.toList());

        List<TestBucket> matchedTests = allDiscoveredTests.stream().map(tuple -> {
            String testName = tuple.getFirst();
            Test task = tuple.getSecond();
            String noAsterixName = testName.substring(0, testName.length() - 1);
            List<Tuple2<String, Double>> matchingTests = allTestsFromCSV.stream().filter(testFromCSV -> testFromCSV.getFirst().startsWith(noAsterixName)).collect(Collectors.toList());
            return new TestBucket(task, testName, noAsterixName, matchingTests);
        }).sorted(Comparator.comparing(TestBucket::getDuration).reversed()).collect(Collectors.toList());

        //use greedy algo - for each testbucket find the currently smallest container and add to it
        matchedTests.forEach(matchedTestBucket -> {
            TestsForForkContainer smallestContainer = Collections.min(forkContainers, Comparator.comparing(TestsForForkContainer::getCurrentDuration));
            smallestContainer.addBucket(matchedTestBucket);
        });

        TestsForForkContainer longestFork = Collections.max(forkContainers, Comparator.comparing(TestsForForkContainer::getCurrentDuration));

        forkContainers.forEach(testsForForkContainer -> {
            System.out.println(testsForForkContainer.getForkIdx() + ": " + testsForForkContainer.getCurrentDuration() +
                    " ( " + testsForForkContainer.getCurrentDuration() / longestFork.getCurrentDuration() + " )");
            testsForForkContainer.freeze();
        });

    }

    public static List<Tuple2<String, Double>> fromCSV(Reader reader) throws IOException {
        String name = "Test Name";
        String duration = "Duration(ms)";
        List<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(reader).getRecords();

        return records.stream().map(record -> {
            String testName = record.get(name);
            String testDuration = record.get(duration);
            return new Tuple2<>(testName, Math.max(Double.parseDouble(testDuration), 10));
        }).sorted(Comparator.comparing(Tuple2::getFirst)).collect(Collectors.toList());
    }

    public static class TestBucket {
        final Test testTask;
        final String nameWithAsterix;
        final String nameWithoutAsterix;
        final List<Tuple2<String, Double>> foundTests;
        final Double duration;

        public TestBucket(Test testTask, String nameWithAsterix, String nameWithoutAsterix, List<Tuple2<String, Double>> foundTests) {
            this.testTask = testTask;
            this.nameWithAsterix = nameWithAsterix;
            this.nameWithoutAsterix = nameWithoutAsterix;
            this.foundTests = foundTests;
            duration = foundTests.stream().mapToDouble(Tuple2::getSecond).sum();
        }

        public Double getDuration() {
            return duration;
        }
    }

    public static class TestsForForkContainer {
        private Double runningDuration = 0.0;
        ;
        private final Integer forkIdx;

        private final List<TestBucket> testsForFork = Collections.synchronizedList(new ArrayList<>());
        private final Map<Test, List<TestBucket>> frozenTests = new HashMap<>();

        public TestsForForkContainer(Integer forkIdx) {
            this.forkIdx = forkIdx;
        }

        public Integer getForkIdx() {
            return this.forkIdx;
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

        public List<String> getTestsForTask(Test task) {
            return frozenTests.getOrDefault(task, Collections.emptyList()).stream().map(it -> it.nameWithAsterix).collect(Collectors.toList());
        }

        public Double getDurationOfBucketForTask(String taskPath) {
            return frozenTests.getOrDefault(taskPath, Collections.emptyList()).stream().mapToDouble(it -> Math.max(it.duration, 1.0)).sum();
        }
    }


}

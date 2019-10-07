package net.corda.testing;

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
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BucketingAllocatorTask extends DefaultTask {
    private static final String DEFAULT_TESTING_TEST_TIMES_CSV = "testing/test-times.csv";
    private final BucketingAllocator allocator;

    @Inject
    public BucketingAllocatorTask(Integer forkCount) {
        Supplier<List<Tuple2<String, Double>>> defaultTestCSV = () -> {
            try {
                FileReader csvSource = new FileReader(new File(BucketingAllocatorTask.this.getProject().getRootDir(), DEFAULT_TESTING_TEST_TIMES_CSV));
                return fromCSV(csvSource);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        this.allocator = new BucketingAllocator(forkCount, defaultTestCSV);
    }

    public void addSource(TestLister source, Test testTask) {
        allocator.addSource(source, testTask);
        this.dependsOn(source);
    }

    public List<String> getTestsForForkAndTestTask(Integer fork, Test testTask) {
        return allocator.getTestsForForkAndTestTask(fork, testTask);
    }

    @TaskAction
    public void allocate(){
        allocator.generateTestPlan();
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

}

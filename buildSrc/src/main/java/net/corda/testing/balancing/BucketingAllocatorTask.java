package net.corda.testing.balancing;

import net.corda.testing.listing.TestLister;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.Test;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BucketingAllocatorTask extends DefaultTask {

    private static final String TEST_NAME = "Test Name";
    private static final String DURATION = "Duration(ms)";
    private static final String DEFAULT_TESTING_TEST_TIMES_CSV = "testing/test-times.csv";

    private final BucketingAllocator allocator;

    @Inject
    public BucketingAllocatorTask(int forkCount) {
        this.allocator = BucketingAllocator.create(forkCount, this::streamFromCSV);
    }

    private Stream<TestTiming> streamFromCSV() {
        try (FileReader csvSource = new FileReader(new File(getProject().getRootDir(), DEFAULT_TESTING_TEST_TIMES_CSV))) {
            return fromCSV(csvSource);
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    public void addSource(TestLister source, Test testTask) {
        allocator.addSource(source, testTask);
        this.dependsOn(source);
    }

    public List<String> getTestIncludesForForkAndTestTask(int fork, Test testTask) {
        return allocator.getTestsForForkAndTestTask(fork, testTask).stream()
                .map(t -> t + "*")
                .collect(Collectors.toList());
    }

    @TaskAction
    public void allocate() {
        allocator.generateTestPlan();
    }

    private static Stream<TestTiming> fromCSV(Reader reader) throws IOException {
        List<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(reader).getRecords();
        return records.stream()
                .map(BucketingAllocatorTask::getTestTiming)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TestTiming::getName));
    }

    @Nullable
    private static TestTiming getTestTiming(CSVRecord record) {
        try {
            String testName = record.get(TEST_NAME);
            String testDuration = record.get(DURATION);
            return TestTiming.of(testName, Math.max(Double.parseDouble(testDuration), 1));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

}

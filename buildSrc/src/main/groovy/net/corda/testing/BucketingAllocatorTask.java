package net.corda.testing;

import groovy.lang.Tuple2;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BucketingAllocatorTask extends DefaultTask {
    private final BucketingAllocator allocator;

    @Inject
    public BucketingAllocatorTask(Integer forkCount) {
        final Supplier<List<Tuple2<String, Double>>> defaultTestFromZip
                = TestArtifacts.getTestsSupplier(BucketingAllocatorTask.this.getProject().getRootDir());

        this.allocator = new BucketingAllocator(forkCount, defaultTestFromZip);
    }

    public void addSource(TestLister source, Test testTask) {
        allocator.addSource(source, testTask);
        this.dependsOn(source);
    }

    public List<String> getTestIncludesForForkAndTestTask(Integer fork, Test testTask) {
        return allocator.getTestsForForkAndTestTask(fork, testTask).stream().map(t -> t + "*").collect(Collectors.toList());
    }

    @TaskAction
    public void allocate() {
        allocator.generateTestPlan();
    }
}

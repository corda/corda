package net.corda.testing;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class BucketingAllocatorTask extends DefaultTask {
    private final BucketingAllocator allocator;

    @Inject
    public BucketingAllocatorTask(Integer forkCount) {
        this.allocator = new BucketingAllocator(forkCount,
                TestDurationArtifacts.getTestsSupplier(BucketingAllocatorTask.this.getProject().getRootDir()));
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

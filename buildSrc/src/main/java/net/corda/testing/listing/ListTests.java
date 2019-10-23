package net.corda.testing.listing;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskAction;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListTests extends DefaultTask implements TestLister {

    public static final String DISTRIBUTION_PROPERTY = "distributeBy";

    private final Distribution distribution = System.getProperty(DISTRIBUTION_PROPERTY) != null ? Distribution.valueOf(System.getProperty(DISTRIBUTION_PROPERTY)) : Distribution.METHOD;

    private FileCollection scanClassPath;
    private List<String> allTests;

    List<?> getTestsForFork(int fork, int forks, int seed) {
        BigInteger gitSha = new BigInteger(getProject().hasProperty("corda_revision") ? getProject().property("corda_revision").toString() : "0", 36);
        if (fork >= forks) {
            throw new IllegalArgumentException("requested shard ${fork + 1} for total shards ${forks}");
        }
        int seedToUse = seed + getPath().hashCode() + gitSha.intValue();
        return new ListShufflerAndAllocator(allTests).getTestsForFork(fork, forks, seedToUse);
    }

    public void setScanClassPath(FileCollection scanClassPath) {
        this.scanClassPath = scanClassPath;
    }

    @Override
    public Stream<String> getAllTestsDiscovered() {
        return allTests.stream();
    }

    @TaskAction
    void discoverTests() {
        switch (distribution) {
            case METHOD:
                allTests = getClassesWithTestAnnotatedMethods()
                        .flatMap( c ->
                            c.getMethodInfo().stream()
                                    .filter( m -> m.hasAnnotation("org.junit.Test") )
                                    .map( m -> c.getName() + "." + m.getName() )
                        )
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
                break;
            case CLASS:
                allTests = getClassesWithTestAnnotatedMethods()
                        .map(c -> c.getName())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
                break;
        }
    }

    private Stream<ClassInfo> getClassesWithTestAnnotatedMethods() {
        return new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .ignoreClassVisibility()
                .ignoreMethodVisibility()
                .enableAnnotationInfo()
                .overrideClasspath(scanClassPath)
                .scan()
                .getClassesWithMethodAnnotation("org.junit.Test")
                .stream()
                .flatMap( c -> Stream.concat(c.getSubclasses().stream(), Stream.of(c)));
    }
}


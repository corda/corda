package net.corda.testing

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction

import java.util.stream.Collectors

interface TestLister {
    List<String> getAllTestsDiscovered()
}

class ListTests extends DefaultTask implements TestLister {

    public static final String DISTRIBUTION_PROPERTY = "distributeBy"

    FileCollection scanClassPath
    List<String> allTests
    DistributeTestsBy distribution = System.getProperty(DISTRIBUTION_PROPERTY) ?
            DistributeTestsBy.valueOf(System.getProperty(DISTRIBUTION_PROPERTY)) : DistributeTestsBy.METHOD

    def getTestsForFork(int fork, int forks, Integer seed) {
        def gitSha = new BigInteger(project.hasProperty("corda_revision") ?
                project.property("corda_revision").toString() : "0", 36)
        if (fork >= forks) {
            throw new IllegalArgumentException("requested shard ${fork + 1} for total shards ${forks}")
        }
        def seedToUse = seed ? (seed + ((String) this.getPath()).hashCode() + gitSha.intValue()) : 0
        return new ListShufflerAndAllocator(allTests).getTestsForFork(fork, forks, seedToUse)
    }

    @Override
    public List<String> getAllTestsDiscovered() {
        return new ArrayList<>(allTests)
    }

    @TaskAction
    def discoverTests() {
        switch (distribution) {
            case DistributeTestsBy.METHOD:
                Collection<String> results = new ClassGraph()
                        .enableClassInfo()
                        .enableMethodInfo()
                        .ignoreClassVisibility()
                        .ignoreMethodVisibility()
                        .enableAnnotationInfo()
                        .overrideClasspath(scanClassPath)
                        .scan()
                        .getClassesWithMethodAnnotation("org.junit.Test")
                        .collect { c -> (c.getSubclasses() + Collections.singletonList(c)) }
                        .flatten()
                        .collect { ClassInfo c ->
                            c.getMethodInfo().filter { m -> m.hasAnnotation("org.junit.Test") }
                                    .collect { m -> c.name + "." + m.name }
                        }.flatten()
                        .toSet()

                this.allTests = results.stream().sorted().collect(Collectors.toList())
                break
            case DistributeTestsBy.CLASS:
                Collection<String> results = new ClassGraph()
                        .enableClassInfo()
                        .enableMethodInfo()
                        .ignoreClassVisibility()
                        .ignoreMethodVisibility()
                        .enableAnnotationInfo()
                        .overrideClasspath(scanClassPath)
                        .scan()
                        .getClassesWithMethodAnnotation("org.junit.Test")
                        .collect { c -> (c.getSubclasses() + Collections.singletonList(c)) }
                        .flatten()
                        .collect { ClassInfo c -> c.name }.flatten()
                        .toSet()
                this.allTests = results.stream().sorted().collect(Collectors.toList())
                break
        }
    }
}

package com.stefano.testing

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction

import java.util.stream.Collectors

class ListShufflerAndAllocator {

    private final List<String> tests

    public ListShufflerAndAllocator(List<String> tests) {
        this.tests = new ArrayList<>(tests)
    }

    def List<String> getTestsForFork(int fork, int forks, Integer seed) {
        Random shuffler = new Random(seed)
        List<String> copy = new ArrayList(tests)
        while (copy.size() < forks) {
            //pad the list
            copy.add(null)
        }
        Collections.shuffle(copy, shuffler)
        int numberOfTestsPerFork = Math.max((copy.size() / forks).intValue(), 1)
        int remainder = copy.size() % numberOfTestsPerFork

        def consumedTests = numberOfTestsPerFork * forks

        def ourStartIdx = numberOfTestsPerFork * fork
        def ourEndIdx = ourStartIdx + numberOfTestsPerFork
        def ourSupplementaryIdx = consumedTests + fork

        return copy

    }
}

class ListTests extends DefaultTask {

    FileCollection scanClassPath
    List<String> allTests


    def getTestsForFork(int fork, int forks, Integer seed) {
        def gitSha = new BigInteger(project.hasProperty("corda_revision") ? project.property("corda_revision").toString() : "0", 36)
        if (fork >= forks) {
            throw new IllegalArgumentException("requested shard ${fork + 1} for total shards ${forks}")
        }
        def seedToUse = seed ? (seed + ((String) this.getPath()).hashCode() + gitSha.intValue()) : 0
        return new ListShufflerAndAllocator(allTests).getTestsForFork(fork, forks, seedToUse)
    }

    @TaskAction
    def discoverTests() {
        Collection<String> results = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .ignoreClassVisibility()
                .ignoreMethodVisibility()
                .enableAnnotationInfo()
                .overrideClasspath(scanClassPath)
                .scan()
                .getClassesWithMethodAnnotation("org.junit.Test")
                .filter { c -> !c.hasAnnotation("org.junit.Ignore") }
                .collect { c -> (c.getSubclasses() + Collections.singletonList(c)) }
                .flatten()
                .collect { ClassInfo c ->
                    c.getMethodInfo().filter { m -> m.hasAnnotation("org.junit.Test") && !m.hasAnnotation("org.junit.Ignore") }.collect { m -> c.name + "." + m.name }
                }.flatten()
                .toSet()

        this.allTests = results.stream().sorted().collect(Collectors.toList())
    }
}
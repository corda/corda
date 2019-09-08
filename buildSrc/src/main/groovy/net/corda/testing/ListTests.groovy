package net.corda.testing

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

import java.util.stream.Collectors

class ListShufflerAndAllocator {

    private final List<String> tests

    public ListShufflerAndAllocator(List<String> tests) {
        this.tests = new ArrayList<>(tests)
    }

    List<String> getTestsForFork(int fork, int forks, Integer seed) {
        Random shuffler = new Random(seed);
        List<String> copy = new ArrayList<>(tests);
        while (copy.size() < forks) {
            //pad the list
            copy.add(null);
        }
        Collections.shuffle(copy, shuffler);
        int numberOfTestsPerFork = Math.max((copy.size() / forks).intValue(), 1);
        int consumedTests = numberOfTestsPerFork * forks;
        int ourStartIdx = numberOfTestsPerFork * fork;
        int ourEndIdx = ourStartIdx + numberOfTestsPerFork;
        int ourSupplementaryIdx = consumedTests + fork;
        ArrayList<String> toReturn = new ArrayList<>(copy.subList(ourStartIdx, ourEndIdx));
        if (ourSupplementaryIdx < copy.size()) {
            toReturn.add(copy.get(ourSupplementaryIdx));
        }
        return toReturn.stream().filter { it -> it != null }.collect(Collectors.toList());
    }
}

class ListTests extends DefaultTask {

    Test testTask
    FileCollection scanClassPath
    List<String> allTests

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
                .collect { c -> (c.getSubclasses() + Collections.singletonList(c)) }
                .flatten()
                .collect { ClassInfo c ->
                    c.getMethodInfo().filter { m -> m.hasAnnotation("org.junit.Test") }.collect { m -> c.name + "." + m.name + "*" }
                }.flatten()
                .toSet()

        this.allTests = results.stream().sorted().collect(Collectors.toList())
    }
}
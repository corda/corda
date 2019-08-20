package com.stefano.testing

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction

import java.util.stream.Collectors

class ListTests extends DefaultTask {

    FileCollection scanClassPath
    List<String> allTests

    def getTestsForFork(int fork, int forks, Integer seed) {
        Random shuffler = new Random(seed ? seed : 0)
        List<String> copy = new ArrayList(allTests)
        Collections.shuffle(copy, shuffler)
        int numberOfTestsPerFork = copy.size() / forks
        int ourRangeStart = fork * numberOfTestsPerFork
        int ourRangeEnd = ((ourRangeStart + 2 * numberOfTestsPerFork) > copy.size()) ?
                copy.size() :
                ourRangeStart + numberOfTestsPerFork
        return copy.subList(ourRangeStart, ourRangeEnd)
    }

    @TaskAction
    def discoverTests() {
        List<String> results = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .overrideClasspath(scanClassPath)
                .scan()
                .getClassesWithMethodAnnotation("org.junit.Test")
                .collect { ClassInfo c ->
                    c.name + ".*"
                }

        this.allTests = results.stream().sorted().collect(Collectors.toList())
    }
}
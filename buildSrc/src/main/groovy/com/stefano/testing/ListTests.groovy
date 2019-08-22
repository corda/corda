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

        if (fork >= forks) {
            throw new IllegalArgumentException("requested shard ${fork + 1} for total shards ${forks}")
        }

        Random shuffler = new Random(seed ? seed : 0)
        List<String> copy = new ArrayList(allTests)
        Collections.shuffle(copy, shuffler)
        int numberOfTestsPerFork = Math.max((copy.size() / forks).intValue(), 1)
        def listOfLists = copy.collate(numberOfTestsPerFork)

        if (fork >= listOfLists.size()) {
            return Collections.emptyList()
        } else {
            //special edge case with some remainder
            if (fork == forks - 1 && listOfLists.size() == forks + 1) {
                return listOfLists[fork] + listOfLists[forks]

            } else {
                return listOfLists[fork]
            }
        }
    }

    @TaskAction
    def discoverTests() {
        List<String> results = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .ignoreClassVisibility()
                .ignoreMethodVisibility()
                .enableAnnotationInfo()
                .overrideClasspath(scanClassPath)
                .scan()
                .getClassesWithMethodAnnotation("org.junit.Test")
                .filter { c -> !c.hasAnnotation("org.junit.Ignore") }
                .collect { ClassInfo c ->
                    c.name + ".*"
                }

        this.allTests = results.stream().sorted().collect(Collectors.toList())
    }
}
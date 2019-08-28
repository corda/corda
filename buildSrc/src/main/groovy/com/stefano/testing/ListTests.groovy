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

        def gitSha = new BigInteger(project.hasProperty("corda_revision") ? project.property("corda_revision").toString() : "0", 36)

        if (fork >= forks) {
            throw new IllegalArgumentException("requested shard ${fork + 1} for total shards ${forks}")
        }

        Random shuffler = new Random(seed ? (seed + ((String) this.getPath()).hashCode() + gitSha.intValue()) : 0)
        List<String> copy = new ArrayList(allTests)
        while (copy.size() < forks) {
            //pad the list
            copy.add(null)
        }
        Collections.shuffle(copy, shuffler)
        int numberOfTestsPerFork = Math.max((copy.size() / forks).intValue(), 1)
        def listOfLists = copy.collate(numberOfTestsPerFork)

        if (fork >= listOfLists.size()) {
            return Collections.emptyList()
        } else {
            //special edge case with some remainder
            if (fork == forks - 1 && listOfLists.size() == forks + 1) {
                return (listOfLists[fork] + listOfLists[forks]).findAll { test -> test != null }

            } else {
                return listOfLists[fork].findAll { test -> test != null }
            }
        }
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
package net.corda.testing

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.util.stream.Collectors

class ListShufflerAndAllocator {

    private final List<String> tests

    ListShufflerAndAllocator(List<String> tests) {
        this.tests = new ArrayList<>(tests)
    }

    List<String> getTestsForFork(int fork, int forks, Integer seed) {
        Random shuffler = new Random(seed)
        List<String> copy = new ArrayList<>(tests)
        while (copy.size() < forks) {
            //pad the list
            copy.add(null)
        }
        Collections.shuffle(copy, shuffler)
        int numberOfTestsPerFork = Math.max((copy.size() / forks).intValue(), 1)
        int consumedTests = numberOfTestsPerFork * forks
        int ourStartIdx = numberOfTestsPerFork * fork
        int ourEndIdx = ourStartIdx + numberOfTestsPerFork
        int ourSupplementaryIdx = consumedTests + fork
        ArrayList<String> toReturn = new ArrayList<>(copy.subList(ourStartIdx, ourEndIdx))
        if (ourSupplementaryIdx < copy.size()) {
            toReturn.add(copy.get(ourSupplementaryIdx))
        }
        return toReturn.stream().filter { it -> it != null }.collect(Collectors.toList())
    }
}

/**
 * This task is called per-project, multiple times
 */
class ListTests extends DefaultTask {
    /// A reference to a list shared between all projects containing all the tests
    private List<String> allTestsForAllProjects

    /// This task is called per-project, so this list will contain only the tests for the current project
    private List<String> testsForThisProjectOnly

    FileCollection scanClassPath

    @Inject
    ListTests(List<String> allTestsForAllProjects) {
        this.allTestsForAllProjects = allTestsForAllProjects
    }

    /**
     * This is called PER-PROJECT, so needs to return 'per project' tests that are going to be run.
     * @param fork
     * @param forks
     * @param seed
     * @return
     */
    def getTestsForFork(int fork, int forks, Integer seed) {
        List<String> tests = new ArrayList<>()

        List<String> testsNotAllocated = new ArrayList<>(this.testsForThisProjectOnly)

        try {
            def testsByDuration = PartitionTestsByDuration.fromTeamCityCsv(new FileReader(project.rootProject.rootDir.path + '/testing/test-durations.csv'))

            def allKnownTests = testsByDuration.keySet()
            // Fill all partitions as if we're going to run all tests.
            // We're going to 'assume' that we're going to run all the tests in the csv, so partition them.
            // Any missing tests, we'll distribute using the original method to a random deterministic bucket.
            def partitioner = new PartitionTestsByDuration(forks, allKnownTests, testsByDuration)
            project.logger.lifecycle(partitioner.summary(fork))

            def allTestsOnThisFork = partitioner.getAllTestsForPartition(fork)
            def projectOnlyTestsOnThisFork =  partitioner.getProjectOnlyTestsForPartition(fork, testsForThisProjectOnly)

            project.logger.lifecycle('+ This task({}) has {} of {} tests on this fork',
                    this.getPath(),
                    projectOnlyTestsOnThisFork.size(),
                    allTestsOnThisFork.size())
            tests.addAll(projectOnlyTestsOnThisFork)

            // Remove all the tests we know about and have allocated.
            // This then leaves 'new' tests in this project that we've never seen before.
            testsNotAllocated.removeAll(allKnownTests)
        }
        catch (FileNotFoundException | IllegalArgumentException e) {
            project.logger.warn('Unable to partition tests by duration:  {}', e.getMessage())
        }

        def gitSha = new BigInteger(project.hasProperty("corda_revision") ? project.property("corda_revision").toString() : "0", 36)
        if (fork >= forks) {
            throw new IllegalArgumentException("requested shard ${fork + 1} for total shards ${forks}")
        }
        def seedToUse = seed ? (seed + ((String) this.getPath()).hashCode() + gitSha.intValue()) : 0
        tests.addAll(new ListShufflerAndAllocator(testsNotAllocated).getTestsForFork(fork, forks, seedToUse))
        tests.sort()

        return tests
    }

    def getTestsForForkOriginal(int fork, int forks, Integer seed) {
        try {
            def testsByDuration = PartitionTestsByDuration.fromTeamCityCsv(new FileReader(project.rootProject.rootDir.path + '/testing/test-durations.csv'))
            def partitioner = new PartitionTestsByDuration(forks, allTestsForAllProjects, testsByDuration)
            project.logger.lifecycle(partitioner.summary(fork))

            def allTestsOnThisFork = partitioner.getAllTestsForPartition(fork)
            def projectOnlyTestsOnThisFork =  partitioner.getProjectOnlyTestsForPartition(fork, testsForThisProjectOnly)

            project.logger.lifecycle('+ This task({}) has {} of {} tests on this fork',
                    this.getPath(),
                    projectOnlyTestsOnThisFork.size(),
                    allTestsOnThisFork.size())
            return projectOnlyTestsOnThisFork
        }
        catch (FileNotFoundException | IllegalArgumentException e) {
            project.logger.warn('Unable to partition tests by duration:  {}', e.getMessage())
        }

        def gitSha = new BigInteger(project.hasProperty("corda_revision") ? project.property("corda_revision").toString() : "0", 36)
        if (fork >= forks) {
            throw new IllegalArgumentException("requested shard ${fork + 1} for total shards ${forks}")
        }
        def seedToUse = seed ? (seed + ((String) this.getPath()).hashCode() + gitSha.intValue()) : 0
        return new ListShufflerAndAllocator(testsForThisProjectOnly).getTestsForFork(fork, forks, seedToUse)
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
                .collect { c -> (c.getSubclasses() + Collections.singletonList(c)) }
                .flatten()
                .collect { ClassInfo c ->
                    c.getMethodInfo()
                            .filter { m -> m.hasAnnotation("org.junit.Test") }
                            .collect { m -> c.name + "." + m.name }
                }.flatten()
                .toSet()

        this.testsForThisProjectOnly = results.stream().sorted().collect(Collectors.toList())

        // Also record the tests for this project/jar in the collection of tests for ALL projects/jars
        this.allTestsForAllProjects.addAll(this.testsForThisProjectOnly)
    }
}
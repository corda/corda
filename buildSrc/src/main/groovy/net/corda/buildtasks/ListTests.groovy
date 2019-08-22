package net.corda.buildtasks

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

class ListTests extends DefaultTask {

    long randomSeed = 0
    int workers = 1
    int workerId = 0

    ListTests() {
        project.afterEvaluate {
            // afterEvaluate in order to allow all plugins to generate dynamic tasks
            // if we don't do this, integrationTest classes don't get generated and the tests are not listed
            dependsOn(project.tasks.findAll {
                it.name.toLowerCase().contains("test") &&
                        it.name.toLowerCase().contains("classes")
            })
        }
    }

    @TaskAction
    def listTests() {
        if (workerId >= workers) throw new IllegalStateException("workerId cannot be >= workers")

        Random rnd = new Random(randomSeed)
        project.getConvention()
                .getPlugin(JavaPluginConvention)
                .sourceSets
                .findAll { it.name.toLowerCase().contains("test") }
                .forEach { ss ->
                    logger.info("Processing source set: {}", ss.name)
                    List<String> tests = scanTests(ss)
                    Collections.shuffle(tests, rnd)
                    String extKey = project.path + "-" + ss.name
                    tests = tests.withIndex()
                            .findAll { Tuple2<String, Integer> ii -> ii.getSecond() % workers == workerId }
                            .collect { it.first }
                    extensions.add(extKey, tests)
                    if (logger.isDebugEnabled())
                        logger.debug("Exposing tests with extension key {}: {}", extKey, tests.join("\n"))
                }
    }

    List<String> scanTests(SourceSet ss) {
        return new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .overrideClasspath(ss.output.classesDirs)
                .scan()
                .getClassesWithMethodAnnotation("org.junit.Test")
                .collect { ClassInfo c -> c.getName() + ".*" }
    }
}

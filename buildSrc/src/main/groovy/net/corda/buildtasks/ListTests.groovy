package net.corda.buildtasks

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

class ListTests extends DefaultTask {

    boolean file = true
    String outputFilePrefix = "tests"

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
        project.getConvention()
                .getPlugin(JavaPluginConvention)
                .sourceSets
                .findAll { it.name.toLowerCase().contains("test") }
                .forEach { ss ->
                    logger.info("Processing source set: {}", ss.name)
                    if (file) {
                        File reportsDir = new File(project.buildDir, "reports/tests")
                        reportsDir.mkdirs()
                        new File(reportsDir, outputFilePrefix + "-" + ss.name)
                                .withWriter("utf-8") {
                                    writeTests(ss, it)
                                }
                    } else {
                        writeTests(ss, new PrintWriter(System.out, true))
                    }
                }
    }

    def writeTests(SourceSet ss, Writer writer) {
        new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .overrideClasspath(ss.output.classesDirs)
                .scan()
                .getClassesWithMethodAnnotation("org.junit.Test")
                .collect { ClassInfo c ->
                    c.getDeclaredMethodInfo()
                            .findAll { mi -> mi.hasAnnotation("org.junit.Test") }
                            .collect { mi -> c.getName() + "." + mi.getName() }
                }
                .flatten()
                .forEach { writer.println it }
    }
}

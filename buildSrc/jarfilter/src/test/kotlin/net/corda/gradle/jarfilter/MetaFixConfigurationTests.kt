package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MetaFixConfigurationTests {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    private lateinit var output: String

    @Before
    fun setup() {
        testProjectDir.installResource("gradle.properties")
    }

    @Test
    fun checkNoJarMeansNoSource() {
        val result = gradleProject("""
plugins {
    id 'java'
    id 'net.corda.gradle.jar-filter'
}

import net.corda.gradle.jarfilter.MetaFixerTask
task metafix(type: MetaFixerTask)
""").build()
        output = result.output
        println(output)

        val metafix = result.forTask("metafix")
        assertEquals(NO_SOURCE, metafix.outcome)
    }

    @Test
    fun checkWithMissingJar() {
        val result = gradleProject("""
plugins {
    id 'net.corda.gradle.jar-filter'
}

import net.corda.gradle.jarfilter.MetaFixerTask
task metafix(type: MetaFixerTask) {
    jars = file('does-not-exist.jar')
}
""").buildAndFail()
        output = result.output
        println(output)

        assertThat(output).containsSubsequence(
            "Caused by: org.gradle.api.GradleException:",
            "(No such file or directory)",
            "Caused by: java.io.FileNotFoundException:",
            "(No such file or directory)"
        )

        val metafix = result.forTask("metafix")
        assertEquals(FAILED, metafix.outcome)
    }

    private fun gradleProject(script: String): GradleRunner {
        testProjectDir.newFile("build.gradle").writeText(script)
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(getBasicArgsForTasks("metafix", "--stacktrace"))
            .withPluginClasspath()
    }

    private fun BuildResult.forTask(name: String): BuildTask {
        return task(":$name") ?: throw AssertionError("No outcome for $name task")
    }
}
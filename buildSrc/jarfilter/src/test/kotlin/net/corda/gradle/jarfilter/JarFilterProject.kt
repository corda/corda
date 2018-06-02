package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.FileNotFoundException
import java.nio.file.Path

class JarFilterProject(private val projectDir: TemporaryFolder, private val name: String) : TestRule {
    private var _sourceJar: Path? = null
    val sourceJar: Path get() = _sourceJar ?: throw FileNotFoundException("Input not found")

    private var _filteredJar: Path? = null
    val filteredJar: Path get() = _filteredJar ?: throw FileNotFoundException("Output not found")

    private var _output: String = ""
    val output: String get() = _output

    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                projectDir.installResources(
                    "$name/build.gradle",
                    "repositories.gradle",
                    "gradle.properties",
                    "settings.gradle"
                )

                val result = GradleRunner.create()
                    .withProjectDir(projectDir.root)
                    .withArguments(getGradleArgsForTasks("jarFilter"))
                    .withPluginClasspath()
                    .build()
                _output = result.output
                println(output)

                val jarFilter = result.task(":jarFilter")
                    ?: throw AssertionError("No outcome for jarFilter task")
                assertEquals(SUCCESS, jarFilter.outcome)

                _sourceJar = projectDir.pathOf("build", "libs", "$name.jar")
                assertThat(sourceJar).isRegularFile()

                _filteredJar = projectDir.pathOf("build", "filtered-libs", "$name-filtered.jar")
                assertThat(filteredJar).isRegularFile()

                statement.evaluate()
            }
        }
    }
}

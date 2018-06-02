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

@Suppress("UNUSED")
class MetaFixProject(private val projectDir: TemporaryFolder, private val name: String) : TestRule {
    private var _sourceJar: Path? = null
    val sourceJar: Path get() = _sourceJar ?: throw FileNotFoundException("Input not found")

    private var _metafixedJar: Path? = null
    val metafixedJar: Path get() = _metafixedJar ?: throw FileNotFoundException("Output not found")

    private var _output: String = ""
    val output: String get() = _output

    override fun apply(base: Statement, description: Description): Statement {
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
                    .withArguments(getGradleArgsForTasks("metafix"))
                    .withPluginClasspath()
                    .build()
                _output = result.output
                println(output)

                val metafix = result.task(":metafix")
                    ?: throw AssertionError("No outcome for metafix task")
                assertEquals(SUCCESS, metafix.outcome)

                _sourceJar = projectDir.pathOf("build", "libs", "$name.jar")
                assertThat(sourceJar).isRegularFile()

                _metafixedJar = projectDir.pathOf("build", "metafixer-libs", "$name-metafixed.jar")
                assertThat(metafixedJar).isRegularFile()

                base.evaluate()
            }
        }
    }
}

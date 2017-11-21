package net.corda.plugins

import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome


class CordformTest {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()
    private var buildFile: File? = null

    @Before
    fun setup() {
        buildFile = testProjectDir.newFile("build.gradle")
    }

    @Test
    fun `test`() {
        val testDsl = """
plugins {
    id 'java'
    id 'net.corda.plugins.cordformation'
}

ext {
    corda_release_version = '1.0.0'
}

task deployNodes(type: net.corda.plugins.Cordform) {

}
            """

        FileUtils.writeStringToFile(buildFile, testDsl, StandardCharsets.UTF_8)
        val result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("deployNodes", "-s")
                .withPluginClasspath()
                .build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
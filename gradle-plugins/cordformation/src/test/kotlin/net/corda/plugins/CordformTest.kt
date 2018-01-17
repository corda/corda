package net.corda.plugins

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
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

    private companion object {
        val cordaFinanceJarName = "corda-finance-3.0-SNAPSHOT"
        val localCordappJarName = "locally-built-cordapp"
        val notaryNodeName = "Notary Service"
    }

    @Before
    fun setup() {
        buildFile = testProjectDir.newFile("build.gradle")
    }

    @Test
    fun `a node with cordapp dependency`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordapp.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceJarName)).exists()
    }

    @Test
    fun `deploy a node with cordapp config`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithCordappConfig.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, cordaFinanceJarName)).exists()
        assertThat(getNodeCordappConfig(notaryNodeName, cordaFinanceJarName)).exists()
    }

    @Test
    fun `deploy the locally built cordapp with cordapp config`() {
        val runner = getStandardGradleRunnerFor("DeploySingleNodeWithLocallyBuildCordappAndConfig.gradle")

        val result = runner.build()

        assertThat(result.task(":deployNodes")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(getNodeCordappJar(notaryNodeName, localCordappJarName)).exists()
        assertThat(getNodeCordappConfig(notaryNodeName, localCordappJarName)).exists()
    }

    private fun createBuildFile(buildFileResourceName: String) = IOUtils.copy(javaClass.getResourceAsStream(buildFileResourceName), buildFile!!.outputStream())
    private fun getStandardGradleRunnerFor(buildFileResourceName: String): GradleRunner {
        createBuildFile(buildFileResourceName)
        return GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("deployNodes", "-s")
                .withPluginClasspath()
    }
    private fun getNodeCordappJar(nodeName: String, cordappJarName: String) = File(testProjectDir.root, "build/nodes/$nodeName/cordapps/$cordappJarName.jar")
    private fun getNodeCordappConfig(nodeName: String, cordappJarName: String) = File(testProjectDir.root, "build/nodes/$nodeName/cordapps/$cordappJarName.conf")
}
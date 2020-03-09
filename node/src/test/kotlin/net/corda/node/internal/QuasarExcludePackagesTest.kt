package net.corda.node.internal

import co.paralleluniverse.fibers.instrument.Retransform
import com.typesafe.config.Config
import net.corda.core.internal.toPath
import net.corda.node.VersionInfo
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.schema.v1.V1NodeConfigurationSpec
import org.junit.Assert
import org.junit.Test

class QuasarExcludePackagesTest {

    @Test(timeout=300_000)
    fun `quasarExcludePackages default is empty list`() {

        // Arrange
        val config = getConfig("working-config.conf")

        // Act
        val nodeConfiguration :NodeConfiguration = V1NodeConfigurationSpec.parse(config).value()

        // Assert
        Assert.assertEquals(emptyList<String>(), nodeConfiguration.quasarExcludePackages)
    }


    @Test(timeout=300_000)
    fun `quasarExcludePackages is read from configuration`() {

        // Arrange
        val config = getConfig("test-config-quasarexcludepackages.conf")

        // Act
        val nodeConfiguration :NodeConfiguration = V1NodeConfigurationSpec.parse(config).value()

        // Assert
        Assert.assertEquals(listOf("net.corda.node.internal.QuasarExcludePackagesTest**"), nodeConfiguration.quasarExcludePackages)
    }

    @Test(timeout=300_000)
    fun `quasarExcludePackages is passed through to QuasarInstrumentor`() {

        // Arrange
        val config = getConfig("test-config-quasarexcludepackages.conf")
        val nodeConfiguration :NodeConfiguration = V1NodeConfigurationSpec.parse(config).value()

        // Act
        Node(nodeConfiguration, VersionInfo.UNKNOWN)

        // Assert
        Assert.assertTrue(Retransform.getInstrumentor().isExcluded("net.corda.node.internal.QuasarExcludePackagesTest.Test"))
    }

    class ResourceMissingException(message: String) : Exception(message)

    private fun getConfig(cfgName: String): Config {
        val resource = this::class.java.classLoader.getResource(cfgName) ?: throw ResourceMissingException("Resource not found")
        val path = resource.toPath()
        return ConfigHelper.loadConfig(path.parent, path)
    }
}

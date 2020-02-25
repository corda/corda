package net.corda.node.internal

import co.paralleluniverse.fibers.instrument.Retransform
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.toPath
import net.corda.node.VersionInfo
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.schema.v1.V1NodeConfigurationSpec
import org.junit.Assert
import org.junit.Test

class QuasarExcludePackagesTest {

    @Test(timeout=300_000)
    fun `quasarExcludePackages is passed through to QuasarInstrumentor`() {

        // Arrange
        val overrides = ConfigFactory.parseMap(mapOf("quasarExcludePackages" to listOf("net.corda.node.internal.QuasarExcludePackagesTest**")))
        val config = getConfig("working-config.conf", overrides)
        val nodeConfiguration :NodeConfiguration = V1NodeConfigurationSpec.parse(config).value()

        // Act
        val node = Node(nodeConfiguration, VersionInfo.UNKNOWN)
        node.stop()

        // Assert
        Assert.assertTrue(Retransform.getInstrumentor().isExcluded("net.corda.node.internal.QuasarExcludePackagesTest.Test"))
    }

    private fun getConfig(cfgName: String, overrides: Config = ConfigFactory.empty()): Config {
        val path = this::class.java.classLoader.getResource(cfgName).toPath()
        return ConfigHelper.loadConfig(
                baseDirectory = path.parent,
                configFile = path,
                configOverrides = overrides
        )
    }
}
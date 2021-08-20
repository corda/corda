package net.corda.node.services.config

import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.verify
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.delete
import net.corda.core.internal.div
import net.corda.node.internal.Node
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.slf4j.Logger
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse

class ConfigHelperTests {
    private var baseDir: Path? = null

    @Before
    fun setup() {
        baseDir = Files.createTempDirectory("corda_config")
    }

    @After
    fun cleanup() {
        baseDir?.delete()
    }

    @Test(timeout = 300_000)
    fun `bad keys are ignored and warned for`() {
        val loggerField = Node::class.java.getDeclaredField("staticLog")
        loggerField.isAccessible = true
        val modifiersField = Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(loggerField, loggerField.modifiers and Modifier.FINAL.inv())
        val originalLogger = loggerField.get(null) as Logger
        val spyLogger = spy(originalLogger)
        loggerField.set(null, spyLogger)

        val config = loadConfig("corda_bad_key" to "2077")

        verify(spyLogger).warn(contains("(property or environment variable) cannot be mapped to an existing Corda"))
        assertFalse(config?.hasPath("corda_bad_key") ?: true)

        loggerField.set(null, originalLogger)
    }

    /**
     * Load the node configuration with the given environment variable
     * overrides.
     *
     * @param environmentVariables pairs of keys and values for environment variables
     * to simulate when loading the configuration.
     */
    @Suppress("SpreadOperator")
    private fun loadConfig(vararg environmentVariables: Pair<String, Any>): Config? {
        return baseDir?.let {
            ConfigHelper.loadConfig(
                    baseDirectory = it,
                    configFile = it / ConfigHelper.DEFAULT_CONFIG_FILENAME,
                    allowMissingConfig = true,
                    configOverrides = ConfigFactory.empty(),
                    rawSystemOverrides = ConfigFactory.empty(),
                    rawEnvironmentOverrides = ConfigFactory.empty().plus(
                            mapOf(*environmentVariables)
                    )
            )
        }
    }
}

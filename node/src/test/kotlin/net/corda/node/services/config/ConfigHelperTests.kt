package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigHelperTests {
    private var baseDir: Path? = null

    @Before
    fun setup() {
        baseDir = Files.createTempDirectory("corda_config")
    }

    @After
    fun cleanup() {
        baseDir?.deleteExisting()
    }

    @Test(timeout = 300_000)
    fun `config is overridden by underscore variable`() {
        val sshPort: Long = 9000

        // Verify the port isn't set when not provided
        var config = loadConfig()
        Assert.assertFalse("SSH port should not be configured when not provided", config!!.hasPath("sshd.port"))

        config = loadConfig("corda_sshd_port" to sshPort)
        Assert.assertEquals(sshPort, config?.getLong("sshd.port"))
    }

    @Test(timeout = 300_000)
    fun `config is overridden by case insensitive underscore variable`() {
        val sshPort: Long = 10000
        val config = loadConfig("CORDA_sshd_port" to sshPort)
        Assert.assertEquals(sshPort, config?.getLong("sshd.port"))
    }

    @Test(timeout = 300_000)
    fun `config is overridden by case insensitive dot variable`() {
        val sshPort: Long = 11000
        val config = loadConfig("CORDA.sshd.port" to sshPort,
                "corda.devMode" to true.toString())
        Assert.assertEquals(sshPort, config?.getLong("sshd.port"))
    }

    @Test(timeout = 300_000)
    fun `shadowing is forbidden`() {
        val sshPort: Long = 12000
        assertThatExceptionOfType(ShadowingException::class.java).isThrownBy {
            loadConfig("CORDA_sshd_port" to sshPort.toString(),
                    "corda.sshd.port" to sshPort.toString())
        }
    }


    @Test(timeout = 300_000)
    fun `bad keys are ignored and warned for`() {
        val outContent = ByteArrayOutputStream()
        val errContent = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        System.setOut(PrintStream(outContent));
        System.setErr(PrintStream(errContent));
        val config = loadConfig("corda_bad_key" to "2077")
        assertTrue(outContent.toString().contains("(property or environment variable) cannot be mapped to an existing Corda"))
        assertFalse(config?.hasPath("corda_bad_key") ?: true)
        System.setOut(originalOut);
        System.setErr(originalErr);
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

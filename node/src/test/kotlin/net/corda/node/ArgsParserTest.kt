package net.corda.node

import joptsimple.OptionException
import net.corda.core.internal.div
import net.corda.nodeapi.internal.crypto.X509KeyStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import org.slf4j.event.Level
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArgsParserTest {
    private val parser = ArgsParser()
    private val workingDirectory = Paths.get(".").normalize().toAbsolutePath()

    @Test
    fun `no command line arguments`() {
        assertThat(parser.parse()).isEqualTo(CmdLineOptions(
                baseDirectory = workingDirectory,
                configFile = workingDirectory / "node.conf",
                help = false,
                logToConsole = false,
                loggingLevel = Level.INFO,
                nodeRegistrationConfig = null,
                isVersion = false,
                noLocalShell = false,
                sshdServer = false,
                justGenerateNodeInfo = false,
                bootstrapRaftCluster = false))
    }

    @Test
    fun `base-directory with relative path`() {
        val expectedBaseDir = Paths.get("tmp").normalize().toAbsolutePath()
        val cmdLineOptions = parser.parse("--base-directory", "tmp")
        assertThat(cmdLineOptions.baseDirectory).isEqualTo(expectedBaseDir)
        assertThat(cmdLineOptions.configFile).isEqualTo(expectedBaseDir / "node.conf")
    }

    @Test
    fun `base-directory with absolute path`() {
        val baseDirectory = Paths.get("tmp").normalize().toAbsolutePath()
        val cmdLineOptions = parser.parse("--base-directory", baseDirectory.toString())
        assertThat(cmdLineOptions.baseDirectory).isEqualTo(baseDirectory)
        assertThat(cmdLineOptions.configFile).isEqualTo(baseDirectory / "node.conf")
    }

    @Test
    fun `config-file with relative path`() {
        val cmdLineOptions = parser.parse("--config-file", "different.conf")
        assertThat(cmdLineOptions.baseDirectory).isEqualTo(workingDirectory)
        assertThat(cmdLineOptions.configFile).isEqualTo(workingDirectory / "different.conf")
    }

    @Test
    fun `config-file with absolute path`() {
        val configFile = Paths.get("tmp", "a.conf").normalize().toAbsolutePath()
        val cmdLineOptions = parser.parse("--config-file", configFile.toString())
        assertThat(cmdLineOptions.baseDirectory).isEqualTo(workingDirectory)
        assertThat(cmdLineOptions.configFile).isEqualTo(configFile)
    }

    @Test
    fun `both base-directory and config-file`() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            parser.parse("--base-directory", "base", "--config-file", "conf")
        }.withMessageContaining("base-directory").withMessageContaining("config-file")
    }

    @Test
    fun `base-directory without argument`() {
        assertThatExceptionOfType(OptionException::class.java).isThrownBy {
            parser.parse("--base-directory")
        }.withMessageContaining("base-directory")
    }

    @Test
    fun `config-file without argument`() {
        assertThatExceptionOfType(OptionException::class.java).isThrownBy {
            parser.parse("--config-file")
        }.withMessageContaining("config-file")
    }

    @Test
    fun `log-to-console`() {
        val cmdLineOptions = parser.parse("--log-to-console")
        assertThat(cmdLineOptions.logToConsole).isTrue()
    }

    @Test
    fun `logging-level`() {
        for (level in Level.values()) {
            val cmdLineOptions = parser.parse("--logging-level", level.name)
            assertThat(cmdLineOptions.loggingLevel).isEqualTo(level)
        }
    }

    @Test
    fun `logging-level without argument`() {
        assertThatExceptionOfType(OptionException::class.java).isThrownBy {
            parser.parse("--logging-level")
        }.withMessageContaining("logging-level")
    }

    @Test
    fun `logging-level with invalid argument`() {
        assertThatExceptionOfType(OptionException::class.java).isThrownBy {
            parser.parse("--logging-level", "not-a-level")
        }.withMessageContaining("logging-level")
    }

    @Test
    fun `initial-registration`() {
        val truststorePath = workingDirectory / "truststore" / "file.jks"
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            parser.parse("--initial-registration", "--network-root-truststore", "$truststorePath", "--network-root-truststore-password", "password-test")
        }.withMessageContaining("Network root trust store path").withMessageContaining("doesn't exist")

        X509KeyStore.fromFile(truststorePath, "dummy_password", createNew = true)

        val cmdLineOptions = parser.parse("--initial-registration", "--network-root-truststore", "$truststorePath", "--network-root-truststore-password", "password-test")
        assertNotNull(cmdLineOptions.nodeRegistrationConfig)
        assertEquals(truststorePath.toAbsolutePath(), cmdLineOptions.nodeRegistrationConfig?.networkRootTrustStorePath)
        assertEquals("password-test", cmdLineOptions.nodeRegistrationConfig?.networkRootTrustStorePassword)
    }

    @Test
    fun version() {
        val cmdLineOptions = parser.parse("--version")
        assertThat(cmdLineOptions.isVersion).isTrue()
    }

    @Test
    fun `generate node infos`() {
        val cmdLineOptions = parser.parse("--just-generate-node-info")
        assertThat(cmdLineOptions.justGenerateNodeInfo).isTrue()
    }

    @Test
    fun `bootstrap raft cluster`() {
        val cmdLineOptions = parser.parse("--bootstrap-raft-cluster")
        assertThat(cmdLineOptions.bootstrapRaftCluster).isTrue()
    }
}

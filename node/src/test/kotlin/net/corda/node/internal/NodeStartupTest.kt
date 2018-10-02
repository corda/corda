package net.corda.node.internal

import net.corda.core.internal.div
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.slf4j.event.Level
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths

class NodeStartupTest {
    private val startup = NodeStartup()

    companion object {
        private lateinit var workingDirectory: Path

        @BeforeClass
        @JvmStatic
        fun initDirectories() {
            workingDirectory = Paths.get(".").normalize().toAbsolutePath()
        }
    }

    @Test
    fun `no command line arguments`() {
        CommandLine.populateCommand(startup)
        assertThat(startup.cmdLineOptions.baseDirectory).isEqualTo(workingDirectory)
        assertThat(startup.cmdLineOptions.configFile).isEqualTo(workingDirectory / "node.conf")
        assertThat(startup.verbose).isEqualTo(false)
        assertThat(startup.loggingLevel).isEqualTo(Level.INFO)
        assertThat(startup.cmdLineOptions.nodeRegistrationOption).isEqualTo(null)
        assertThat(startup.cmdLineOptions.noLocalShell).isEqualTo(false)
        assertThat(startup.cmdLineOptions.sshdServer).isEqualTo(false)
        assertThat(startup.cmdLineOptions.justGenerateNodeInfo).isEqualTo(false)
        assertThat(startup.cmdLineOptions.justGenerateRpcSslCerts).isEqualTo(false)
        assertThat(startup.cmdLineOptions.bootstrapRaftCluster).isEqualTo(false)
        assertThat(startup.cmdLineOptions.unknownConfigKeysPolicy).isEqualTo(UnknownConfigKeysPolicy.FAIL)
        assertThat(startup.cmdLineOptions.devMode).isEqualTo(null)
        assertThat(startup.cmdLineOptions.clearNetworkMapCache).isEqualTo(false)
        assertThat(startup.cmdLineOptions.networkRootTrustStorePath).isEqualTo(workingDirectory / "certificates" / "network-root-truststore.jks")
    }

    @Test
    fun `--base-directory`() {
        CommandLine.populateCommand(startup, "--base-directory", (workingDirectory / "another-base-dir").toString())
        assertThat(startup.cmdLineOptions.baseDirectory).isEqualTo(workingDirectory / "another-base-dir")
        assertThat(startup.cmdLineOptions.configFile).isEqualTo(workingDirectory / "another-base-dir" / "node.conf")
        assertThat(startup.cmdLineOptions.networkRootTrustStorePath).isEqualTo(workingDirectory / "another-base-dir" / "certificates" / "network-root-truststore.jks")
    }
}

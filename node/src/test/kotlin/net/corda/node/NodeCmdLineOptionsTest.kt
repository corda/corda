package net.corda.node

import net.corda.core.internal.div
import net.corda.node.internal.NodeStartup
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import org.apache.logging.log4j.Level
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class NodeCmdLineOptionsTest {
    private val parser = NodeStartup()

    companion object {
        private lateinit var workingDirectory: Path
        private lateinit var buildDirectory: Path

        @BeforeClass
        @JvmStatic
        fun initDirectories() {
            workingDirectory = Paths.get(".").normalize().toAbsolutePath()
            buildDirectory = workingDirectory.resolve("build")
        }
    }

    @Test
    fun `no command line arguments`() {
        assertThat(parser.cmdLineOptions.baseDirectory.normalize().toAbsolutePath()).isEqualTo(workingDirectory)
        assertThat(parser.cmdLineOptions.configFile.normalize().toAbsolutePath()).isEqualTo(workingDirectory / "node.conf")
        assertThat(parser.verbose).isEqualTo(false)
        assertThat(parser.loggingLevel).isEqualTo(Level.INFO)
        assertThat(parser.cmdLineOptions.nodeRegistrationOption).isEqualTo(null)
        assertThat(parser.cmdLineOptions.noLocalShell).isEqualTo(false)
        assertThat(parser.cmdLineOptions.sshdServer).isEqualTo(false)
        assertThat(parser.cmdLineOptions.justGenerateNodeInfo).isEqualTo(false)
        assertThat(parser.cmdLineOptions.justGenerateRpcSslCerts).isEqualTo(false)
        assertThat(parser.cmdLineOptions.bootstrapRaftCluster).isEqualTo(false)
        assertThat(parser.cmdLineOptions.unknownConfigKeysPolicy).isEqualTo(UnknownConfigKeysPolicy.FAIL)
        assertThat(parser.cmdLineOptions.devMode).isEqualTo(null)
        assertThat(parser.cmdLineOptions.clearNetworkMapCache).isEqualTo(false)
    }
}

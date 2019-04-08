package net.corda.node.internal

import net.corda.cliutils.CommonCliConstants
import net.corda.core.internal.div
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import org.assertj.core.api.Assertions
import org.junit.BeforeClass
import org.junit.Test
import org.slf4j.event.Level
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths

class NodeStartupCliTest {
    private val startup = NodeStartupCli()

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
        Assertions.assertThat(startup.cmdLineOptions.baseDirectory).isEqualTo(workingDirectory)
        Assertions.assertThat(startup.cmdLineOptions.configFile).isEqualTo(workingDirectory / "node.conf")
        Assertions.assertThat(startup.verbose).isEqualTo(false)
        Assertions.assertThat(startup.loggingLevel).isEqualTo(Level.INFO)
        Assertions.assertThat(startup.cmdLineOptions.noLocalShell).isEqualTo(false)
        Assertions.assertThat(startup.cmdLineOptions.sshdServer).isEqualTo(false)
        Assertions.assertThat(startup.cmdLineOptions.justGenerateNodeInfo).isEqualTo(false)
        Assertions.assertThat(startup.cmdLineOptions.justGenerateRpcSslCerts).isEqualTo(false)
        Assertions.assertThat(startup.cmdLineOptions.unknownConfigKeysPolicy)
                .isEqualTo(UnknownConfigKeysPolicy.FAIL)
        Assertions.assertThat(startup.cmdLineOptions.devMode).isEqualTo(null)
        Assertions.assertThat(startup.cmdLineOptions.clearNetworkMapCache).isEqualTo(false)
        Assertions.assertThat(startup.cmdLineOptions.networkRootTrustStorePathParameter).isEqualTo(null)
    }

    @Test
    fun `--base-directory`() {
        CommandLine.populateCommand(startup, CommonCliConstants.BASE_DIR, (workingDirectory / "another-base-dir").toString())
        Assertions.assertThat(startup.cmdLineOptions.baseDirectory).isEqualTo(workingDirectory / "another-base-dir")
        Assertions.assertThat(startup.cmdLineOptions.configFile).isEqualTo(workingDirectory / "another-base-dir" / "node.conf")
        Assertions.assertThat(startup.cmdLineOptions.networkRootTrustStorePathParameter).isEqualTo(null)
    }
}
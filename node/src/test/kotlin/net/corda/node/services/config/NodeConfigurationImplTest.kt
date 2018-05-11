package net.corda.node.services.config

import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.tools.shell.SSHDConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.URI
import java.nio.file.Paths
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeConfigurationImplTest {
    @Test
    fun `can't have dev mode options if not in dev mode`() {
        val debugOptions = DevModeOptions()
        configDebugOptions(true, debugOptions)
        configDebugOptions(true, null)
        assertThatThrownBy { configDebugOptions(false, debugOptions) }.hasMessageMatching("Cannot use devModeOptions outside of dev mode")
        configDebugOptions(false, null)
    }

    @Test
    fun `check devModeOptions flag helper`() {
        assertTrue { configDebugOptions(true, null).shouldCheckCheckpoints() }
        assertTrue { configDebugOptions(true, DevModeOptions()).shouldCheckCheckpoints() }
        assertTrue { configDebugOptions(true, DevModeOptions(false)).shouldCheckCheckpoints() }
        assertFalse { configDebugOptions(true, DevModeOptions(true)).shouldCheckCheckpoints() }
    }

    @Test
    fun `check crashShell flags helper`() {
        assertFalse { testConfiguration.copy(sshd = null).shouldStartSSHDaemon() }
        assertTrue { testConfiguration.copy(sshd = SSHDConfiguration(1234)).shouldStartSSHDaemon() }
        assertFalse { testConfiguration.copy(noLocalShell = true).shouldStartLocalShell() }
        assertFalse { testConfiguration.copy(noLocalShell = false, devMode = false).shouldStartLocalShell() }
        assertFalse { testConfiguration.copy(noLocalShell = false, devMode = true).shouldStartLocalShell() }
        assertFalse { testConfiguration.copy(noLocalShell = true).shouldInitCrashShell() }
        assertFalse { testConfiguration.copy(sshd = null).shouldInitCrashShell() }
        assertFalse { testConfiguration.copy(noLocalShell = true, sshd = null).shouldInitCrashShell() }
    }

    @Test
    fun `validation has error when compatibilityZoneURL is present and devMode is true`() {
        val configuration = testConfiguration.copy(devMode = true, compatibilityZoneURL = URI.create("https://r3.com").toURL())

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying { error -> error.contains("compatibilityZoneURL") && error.contains("devMode") }
    }

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?): NodeConfiguration {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private fun testConfiguration(dataSourceProperties: Properties): NodeConfigurationImpl {
        return testConfiguration.copy(dataSourceProperties = dataSourceProperties)
    }

    private val testConfiguration = testNodeConfiguration()

    private fun testNodeConfiguration(): NodeConfigurationImpl {
        val baseDirectory = Paths.get(".")
        val keyStorePassword = "cordacadevpass"
        val trustStorePassword = "trustpass"
        val rpcSettings = NodeRpcSettings(
                address = NetworkHostAndPort("localhost", 1),
                adminAddress = NetworkHostAndPort("localhost", 2),
                standAloneBroker = false,
                useSsl = false,
                ssl = null)
        return NodeConfigurationImpl(
                baseDirectory = baseDirectory,
                myLegalName = ALICE_NAME,
                emailAddress = "",
                keyStorePassword = keyStorePassword,
                trustStorePassword = trustStorePassword,
                dataSourceProperties = makeTestDataSourceProperties(ALICE_NAME.organisation),
                rpcUsers = emptyList(),
                verifierType = VerifierType.InMemory,
                p2pAddress = NetworkHostAndPort("localhost", 0),
                messagingServerAddress = null,
                p2pMessagingRetry = P2PMessagingRetryConfiguration(5.seconds, 3, 1.0),
                notary = null,
                devMode = true,
                noLocalShell = false,
                rpcSettings = rpcSettings,
                crlCheckSoftFail = true
        )
    }
}

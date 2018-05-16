package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.div
import net.corda.core.internal.toPath
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.tools.shell.SSHDConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.URI
import java.net.URL
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
    fun `can't have tlsCertCrlDistPoint null when tlsCertCrlIssuer is given`() {
        val configValidationResult = configTlsCertCrlOptions(null, "C=US, L=New York, OU=Corda, O=R3 HoldCo LLC, CN=Corda Root CA").validate()
        assertTrue { configValidationResult.isNotEmpty() }
        assertThat(configValidationResult.first()).contains("tlsCertCrlDistPoint needs to be specified when tlsCertCrlIssuer is not NULL")
    }

    @Test
    fun `tlsCertCrlIssuer validation fails when misconfigured`() {
        val configValidationResult = configTlsCertCrlOptions(URL("http://test.com/crl"), "Corda Root CA").validate()
        assertTrue { configValidationResult.isNotEmpty() }
        assertThat(configValidationResult.first()).contains("Error when parsing tlsCertCrlIssuer:")
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
    fun `Dev mode is autodetected correctly`() {
        val os = System.getProperty("os.name")

        setSystemOs("Windows 98")
        assertTrue(getConfig("test-config-empty.conf").getBoolean("devMode"))

        setSystemOs("Mac Sierra")
        assertTrue(getConfig("test-config-empty.conf").getBoolean("devMode"))

        setSystemOs("Windows server 2008")
        assertFalse(getConfig("test-config-empty.conf").getBoolean("devMode"))

        setSystemOs("Linux")
        assertFalse(getConfig("test-config-empty.conf").getBoolean("devMode"))

        setSystemOs(os)
    }

    private fun setSystemOs(os: String) {
        System.setProperty("os.name", os)
    }

    @Test
    fun `Dev mode is read from the config over the autodetect logic`() {
        assertTrue(getConfig("test-config-DevMode.conf").getBoolean("devMode"))
        assertFalse(getConfig("test-config-noDevMode.conf").getBoolean("devMode"))
    }

    @Test
    fun `Dev mode is true if overriden`() {
        assertTrue(getConfig("test-config-DevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBoolean("devMode"))
        assertTrue(getConfig("test-config-noDevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBoolean("devMode"))
        assertTrue(getConfig("test-config-empty.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBoolean("devMode"))
    }

    @Test
    fun `Dev mode is false if overriden`() {
        assertFalse(getConfig("test-config-DevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to false))).getBoolean("devMode"))
        assertFalse(getConfig("test-config-noDevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to false))).getBoolean("devMode"))
        assertFalse(getConfig("test-config-empty.conf", ConfigFactory.parseMap(mapOf("devMode" to false))).getBoolean("devMode"))
    }

    private fun getConfig(cfgName: String, overrides: Config = ConfigFactory.empty()): Config {
        val path = this::class.java.classLoader.getResource(cfgName).toPath()
        val cfg = ConfigHelper.loadConfig(
                baseDirectory = path.parent,
                configFile = path,
                configOverrides = overrides
        )
        return cfg
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

    private fun configTlsCertCrlOptions(tlsCertCrlDistPoint: URL?, tlsCertCrlIssuer: String?): NodeConfiguration {
        return testConfiguration.copy(tlsCertCrlDistPoint = tlsCertCrlDistPoint, tlsCertCrlIssuer = tlsCertCrlIssuer)
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
                ssl = SslOptions(baseDirectory / "certificates", keyStorePassword, trustStorePassword, true))
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
                certificateChainCheckPolicies = emptyList(),
                devMode = true,
                noLocalShell = false,
                rpcSettings = rpcSettings,
                crlCheckSoftFail = true,
                tlsCertCrlDistPoint = null
        )
    }
}

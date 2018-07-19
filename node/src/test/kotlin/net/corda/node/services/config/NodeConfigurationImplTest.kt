package net.corda.node.services.config

import com.typesafe.config.*
import net.corda.core.internal.toPath
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.tools.shell.SSHDConfiguration
import org.assertj.core.api.Assertions.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import javax.security.auth.x500.X500Principal
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
    fun `can't have tlsCertCrlDistPoint null when crlCheckSoftFail is false`() {
        val configValidationResult = configTlsCertCrlOptions(null, null, false).validate()
        assertTrue { configValidationResult.isNotEmpty() }
        assertThat(configValidationResult.first()).contains("tlsCertCrlDistPoint needs to be specified when crlCheckSoftFail is FALSE")
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
        val configuration = testConfiguration.copy(
                devMode = true,
                compatibilityZoneURL = URL("https://r3.com"))

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying { error -> error.contains("compatibilityZoneURL") && error.contains("devMode") }
    }

    @Test
    fun `validation succeeds when compatibilityZoneURL is present and devMode is true and allowCompatibilityZoneURL is set`() {
        val configuration = testConfiguration.copy(
                devMode = true,
                compatibilityZoneURL = URL("https://r3.com"),
                devModeOptions = DevModeOptions(allowCompatibilityZone = true))

        val errors = configuration.validate()
        assertThat(errors).isEmpty()
    }

    @Test
    fun `errors for nested config keys contain path`() {
        var rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
        val missingPropertyPath = "rpcSettings.address"
        rawConfig = rawConfig.withoutPath(missingPropertyPath)

        assertThatThrownBy { rawConfig.parseAsNodeConfiguration() }.isInstanceOfSatisfying(ConfigException.Missing::class.java) { exception ->
            assertThat(exception.message).isNotNull()
            assertThat(exception.message).contains(missingPropertyPath)
        }
    }

    @Test
    fun `validation has error when compatibilityZone is present and devMode is true`() {
        val configuration = testConfiguration.copy(devMode = true, networkServices = NetworkServicesConfig(
                URL("https://r3.com.doorman"),
                URL("https://r3.com/nm")))

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying { error -> error.contains("networkServices") && error.contains("devMode") }
    }

    @Test
    fun `validation has error when both compatibilityZoneURL and networkServices are configured`() {
        val configuration = testConfiguration.copy(
                devMode = false,
                compatibilityZoneURL = URL("https://r3.com"),
                networkServices = NetworkServicesConfig(
                        URL("https://r3.com.doorman"),
                        URL("https://r3.com/nm")))

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying {
            error -> error.contains("Cannot configure both compatibilityZoneUrl and networkServices simultaneously")
        }
    }

    @Test
    fun `rpcAddress and rpcSettings_address are equivalent`() {
        var rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
        rawConfig = rawConfig.withoutPath("rpcSettings.address")
        rawConfig = rawConfig.withValue("rpcAddress", ConfigValueFactory.fromAnyRef("localhost:4444"))

        assertThatCode { rawConfig.parseAsNodeConfiguration() }.doesNotThrowAnyException()
    }

    @Test
    fun `missing rpcSettings_adminAddress cause a graceful failure`() {
        var rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
        rawConfig = rawConfig.withoutPath("rpcSettings.adminAddress")

        val config = rawConfig.parseAsNodeConfiguration()

        assertThat(config.validate().filter { it.contains("rpcSettings.adminAddress") }).isNotEmpty
    }

    @Test
    fun `compatiilityZoneURL populates NetworkServices`() {
        val compatibilityZoneURL = URI.create("https://r3.com").toURL()
        val configuration = testConfiguration.copy(
                devMode = false,
                compatibilityZoneURL = compatibilityZoneURL)

        assertNotNull(configuration.networkServices)
        assertEquals(compatibilityZoneURL, configuration.networkServices!!.doormanURL)
        assertEquals(compatibilityZoneURL, configuration.networkServices!!.networkMapURL)
    }

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?): NodeConfiguration {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private fun configTlsCertCrlOptions(tlsCertCrlDistPoint: URL?, tlsCertCrlIssuer: String?, crlCheckSoftFail: Boolean = true): NodeConfiguration {
        return testConfiguration.copy(tlsCertCrlDistPoint = tlsCertCrlDistPoint, tlsCertCrlIssuer = tlsCertCrlIssuer?.let { X500Principal(it) }, crlCheckSoftFail = crlCheckSoftFail)
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
                flowTimeout = FlowTimeoutConfiguration(5.seconds, 3, 1.0),
                notary = null,
                devMode = true,
                noLocalShell = false,
                rpcSettings = rpcSettings,
                crlCheckSoftFail = true,
                tlsCertCrlDistPoint = null
        )
    }
}

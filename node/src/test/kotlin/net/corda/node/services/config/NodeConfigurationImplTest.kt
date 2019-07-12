package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.core.internal.div
import net.corda.core.internal.toPath
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.config.getBooleanCaseInsensitive
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.tools.shell.SSHDConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeConfigurationImplTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

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
        assertThat(configValidationResult.first()).contains("tlsCertCrlDistPoint")
        assertThat(configValidationResult.first()).contains("tlsCertCrlIssuer")
    }

    @Test
    fun `can't have tlsCertCrlDistPoint null when crlCheckSoftFail is false`() {
        val configValidationResult = configTlsCertCrlOptions(null, null, false).validate()
        assertTrue { configValidationResult.isNotEmpty() }
        assertThat(configValidationResult.first()).contains("tlsCertCrlDistPoint")
        assertThat(configValidationResult.first()).contains("crlCheckSoftFail")
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
        assertTrue(getConfig("test-config-empty.conf").getBooleanCaseInsensitive("devMode"))

        setSystemOs("Mac Sierra")
        assertTrue(getConfig("test-config-empty.conf").getBooleanCaseInsensitive("devMode"))

        setSystemOs("Windows server 2008")
        assertFalse(getConfig("test-config-empty.conf").getBooleanCaseInsensitive("devMode"))

        setSystemOs("Linux")
        assertFalse(getConfig("test-config-empty.conf").getBooleanCaseInsensitive("devMode"))

        setSystemOs(os)
    }

    private fun setSystemOs(os: String) {
        System.setProperty("os.name", os)
    }

    @Test
    fun `Dev mode is read from the config over the autodetect logic`() {
        assertTrue(getConfig("test-config-DevMode.conf").getBooleanCaseInsensitive("devMode"))
        assertFalse(getConfig("test-config-noDevMode.conf").getBooleanCaseInsensitive("devMode"))
    }

    @Test
    fun `Dev mode is true if overriden`() {
        assertTrue(getConfig("test-config-DevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBooleanCaseInsensitive("devMode"))
        assertTrue(getConfig("test-config-noDevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBooleanCaseInsensitive("devMode"))
        assertTrue(getConfig("test-config-empty.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBooleanCaseInsensitive("devMode"))
    }

    @Test
    fun `Dev mode is false if overriden`() {
        assertFalse(getConfig("test-config-DevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to false))).getBooleanCaseInsensitive("devMode"))
        assertFalse(getConfig("test-config-noDevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to false))).getBooleanCaseInsensitive("devMode"))
        assertFalse(getConfig("test-config-empty.conf", ConfigFactory.parseMap(mapOf("devMode" to false))).getBooleanCaseInsensitive("devMode"))
    }

    private fun getConfig(cfgName: String, overrides: Config = ConfigFactory.empty()): Config {
        val path = this::class.java.classLoader.getResource(cfgName).toPath()
        return ConfigHelper.loadConfig(
                baseDirectory = path.parent,
                configFile = path,
                configOverrides = overrides
        )
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

        assertThat(rawConfig.parseAsNodeConfiguration().errors.single()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->
            assertThat(error.message).contains(missingPropertyPath)
            assertThat(error.typeName).isEqualTo(NodeConfiguration::class.java.simpleName)
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

        assertThat(errors).hasOnlyOneElementSatisfying { error ->
            error.contains("Cannot configure both compatibilityZoneUrl and networkServices simultaneously")
        }
    }

    @Test
    fun `rpcAddress and rpcSettings_address are equivalent`() {
        var rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
        rawConfig = rawConfig.withoutPath("rpcSettings.address")
        rawConfig = rawConfig.withValue("rpcAddress", ConfigValueFactory.fromAnyRef("localhost:4444"))

        assertThat(rawConfig.parseAsNodeConfiguration().isValid).isTrue()
    }

    @Test
    fun `relative path correctly parsed`() {
        val rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))

        // Override base directory to have predictable experience on diff OSes
        val finalConfig = configOf(
                // Add substitution values here
                "baseDirectory" to tempFolder.root.canonicalPath)
                .withFallback(rawConfig)
                .resolve()

        val nodeConfiguration = finalConfig.parseAsNodeConfiguration()
        assertThat(nodeConfiguration.isValid).isTrue()

        val baseDirPath = tempFolder.root.toPath()
        assertEquals(listOf(baseDirPath / "./myCorDapps1", baseDirPath / "./myCorDapps2"), nodeConfiguration.value().cordappDirectories)
    }

    @Test
    fun `missing rpcSettings_adminAddress cause a graceful failure`() {
        var rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
        rawConfig = rawConfig.withoutPath("rpcSettings.adminAddress")

        val config = rawConfig.parseAsNodeConfiguration()

        assertThat(config.errors.asSequence().map(Configuration.Validation.Error::message).filter { it.contains("rpcSettings.adminAddress") }.toList()).isNotEmpty
    }

    @Test
    fun `compatibilityZoneURL populates NetworkServices`() {
        val compatibilityZoneURL = URI.create("https://r3.com").toURL()
        val configuration = testConfiguration.copy(
                devMode = false,
                compatibilityZoneURL = compatibilityZoneURL)

        assertNotNull(configuration.networkServices)
        assertEquals(compatibilityZoneURL, configuration.networkServices!!.doormanURL)
        assertEquals(compatibilityZoneURL, configuration.networkServices!!.networkMapURL)
    }

    @Test
    fun `jmxReporterType is null and defaults to Jokolia`() {
        val rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("devMode" to true)))
        val nodeConfig = rawConfig.parseAsNodeConfiguration().value()
        assertEquals(JmxReporterType.JOLOKIA.toString(), nodeConfig.jmxReporterType.toString())
    }

    @Test
    fun `jmxReporterType is not null and is set to New Relic`() {
        var rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("devMode" to true)))
        rawConfig = rawConfig.withValue("jmxReporterType", ConfigValueFactory.fromAnyRef("NEW_RELIC"))
        val nodeConfig = rawConfig.parseAsNodeConfiguration().value()
        assertEquals(JmxReporterType.NEW_RELIC.toString(), nodeConfig.jmxReporterType.toString())
    }

    @Test
    fun `jmxReporterType is not null and set to Jokolia`() {
        var rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("devMode" to true)))
        rawConfig = rawConfig.withValue("jmxReporterType", ConfigValueFactory.fromAnyRef("JOLOKIA"))
        val nodeConfig = rawConfig.parseAsNodeConfiguration().value()
        assertEquals(JmxReporterType.JOLOKIA.toString(), nodeConfig.jmxReporterType.toString())
    }

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?): NodeConfigurationImpl {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private fun configTlsCertCrlOptions(tlsCertCrlDistPoint: URL?, tlsCertCrlIssuer: String?, crlCheckSoftFail: Boolean = true): NodeConfigurationImpl {
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
                tlsCertCrlDistPoint = null,
                flowOverrides = FlowOverrideConfig(listOf())
        )
    }
}

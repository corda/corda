package net.corda.node.services.config

import com.nhaarman.mockito_kotlin.mock
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.core.internal.div
import net.corda.core.internal.toPath
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.node.services.config.shell.SSHDConfiguration
import net.corda.nodeapi.internal.config.getBooleanCaseInsensitive
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
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

    @Test(timeout=6_000)
	fun `can't have dev mode options if not in dev mode`() {
        val debugOptions = DevModeOptions()
        configDebugOptions(true, debugOptions)
        configDebugOptions(true, null)
        assertThatThrownBy { configDebugOptions(false, debugOptions) }.hasMessageMatching("Cannot use devModeOptions outside of dev mode")
        configDebugOptions(false, null)
    }

    @Test(timeout=6_000)
	fun `can't have tlsCertCrlDistPoint null when tlsCertCrlIssuer is given`() {
        val configValidationResult = configTlsCertCrlOptions(null, "C=US, L=New York, OU=Corda, O=R3 HoldCo LLC, CN=Corda Root CA").validate()
        assertTrue { configValidationResult.isNotEmpty() }
        assertThat(configValidationResult.first()).contains("tlsCertCrlDistPoint")
        assertThat(configValidationResult.first()).contains("tlsCertCrlIssuer")
    }

    @Test(timeout=6_000)
	fun `can't have tlsCertCrlDistPoint null when crlCheckSoftFail is false`() {
        val configValidationResult = configTlsCertCrlOptions(null, null, false).validate()
        assertTrue { configValidationResult.isNotEmpty() }
        assertThat(configValidationResult.first()).contains("tlsCertCrlDistPoint")
        assertThat(configValidationResult.first()).contains("crlCheckSoftFail")
    }

    @Test(timeout=6_000)
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

    @Test(timeout=6_000)
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

    @Test(timeout=6_000)
	fun `Dev mode is read from the config over the autodetect logic`() {
        assertTrue(getConfig("test-config-DevMode.conf").getBooleanCaseInsensitive("devMode"))
        assertFalse(getConfig("test-config-noDevMode.conf").getBooleanCaseInsensitive("devMode"))
    }

    @Test(timeout=6_000)
	fun `Dev mode is true if overriden`() {
        assertTrue(getConfig("test-config-DevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBooleanCaseInsensitive("devMode"))
        assertTrue(getConfig("test-config-noDevMode.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBooleanCaseInsensitive("devMode"))
        assertTrue(getConfig("test-config-empty.conf", ConfigFactory.parseMap(mapOf("devMode" to true))).getBooleanCaseInsensitive("devMode"))
    }

    @Test(timeout=6_000)
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

    @Test(timeout=6_000)
	fun `validation has error when compatibilityZoneURL is present and devMode is true`() {
        val configuration = testConfiguration.copy(
                devMode = true,
                compatibilityZoneURL = URL("https://r3.com"))

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying { error -> error.contains("compatibilityZoneURL") && error.contains("devMode") }
    }

    @Test(timeout=6_000)
	fun `validation succeeds when compatibilityZoneURL is present and devMode is true and allowCompatibilityZoneURL is set`() {
        val configuration = testConfiguration.copy(
                devMode = true,
                compatibilityZoneURL = URL("https://r3.com"),
                devModeOptions = DevModeOptions(allowCompatibilityZone = true))

        val errors = configuration.validate()
        assertThat(errors).isEmpty()
    }

    @Test(timeout=6_000)
	fun `errors for nested config keys contain path`() {
        val missingPropertyPath = "rpcSettings.address"
        val config = rawConfig.withoutPath(missingPropertyPath)

        assertThat(config.parseAsNodeConfiguration().errors.single()).isInstanceOfSatisfying(Configuration.Validation.Error.MissingValue::class.java) { error ->
            assertThat(error.message).contains(missingPropertyPath)
            assertThat(error.typeName).isEqualTo(NodeConfiguration::class.java.simpleName)
        }
    }

    @Test(timeout=6_000)
	fun `validation has error when compatibilityZone is present and devMode is true`() {
        val configuration = testConfiguration.copy(devMode = true, networkServices = NetworkServicesConfig(
                URL("https://r3.com.doorman"),
                URL("https://r3.com/nm")))

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying { error -> error.contains("networkServices") && error.contains("devMode") }
    }

    @Test(timeout=6_000)
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

    @Test(timeout=6_000)
	fun `rpcAddress and rpcSettings_address are equivalent`() {
        var config = rawConfig.withoutPath("rpcSettings.address")
        config = config.withValue("rpcAddress", ConfigValueFactory.fromAnyRef("localhost:4444"))

        assertThat(config.parseAsNodeConfiguration().isValid).isTrue()
    }

    @Test(timeout=6_000)
	fun `absolute base directory leads to correct cordapp directories`() {
        // Override base directory to have predictable experience on diff OSes
        val finalConfig = configOf(
                // Add substitution values here
                "baseDirectory" to tempFolder.root.canonicalPath)
                .withFallback(rawConfig)
                .resolve()

        val nodeConfiguration = finalConfig.parseAsNodeConfiguration()
        assertTrue(nodeConfiguration.isValid)

        val baseDirPath = tempFolder.root.toPath()
        assertEquals(listOf(baseDirPath / "./myCorDapps1", baseDirPath / "./myCorDapps2"), nodeConfiguration.value().cordappDirectories)
    }

    @Test(timeout=6_000)
    fun `absolute base directory leads to correct default cordapp directory`() {
        // Override base directory to have predictable experience on diff OSes
        val finalConfig = configOf(
                // Add substitution values here
                "baseDirectory" to tempFolder.root.canonicalPath)
                .withFallback(rawConfigNoCordapps)
                .resolve()

        val nodeConfiguration = finalConfig.parseAsNodeConfiguration()
        assertTrue(nodeConfiguration.isValid)

        val baseDirPath = tempFolder.root.toPath()
        assertEquals(listOf(baseDirPath / "cordapps"), nodeConfiguration.value().cordappDirectories)
    }

    @Test(timeout=6_000)
    fun `relative base dir leads to correct cordapp directories`() {
        val path = tempFolder.root.relativeTo(tempFolder.root.parentFile).toString()
        val fullPath = File(".").resolve(path).toString()
        // Override base directory to have predictable experience on diff OSes
        val finalConfig = configOf(
                // Add substitution values here
                "baseDirectory" to fullPath)
                .withFallback(rawConfig)
                .resolve()

        val nodeConfiguration = finalConfig.parseAsNodeConfiguration()
        assertTrue(nodeConfiguration.isValid)

        assertEquals(listOf(fullPath / "./myCorDapps1", fullPath / "./myCorDapps2"), nodeConfiguration.value().cordappDirectories)
    }

    @Test(timeout=6_000)
    fun `relative base dir leads to correct default cordapp directory`() {
        val path = tempFolder.root.relativeTo(tempFolder.root.parentFile).toString()
        val fullPath = File(".").resolve(path).toString()
        // Override base directory to have predictable experience on diff OSes
        val finalConfig = configOf(
                // Add substitution values here
                "baseDirectory" to fullPath)
                .withFallback(rawConfigNoCordapps)
                .resolve()

        val nodeConfiguration = finalConfig.parseAsNodeConfiguration()
        assertTrue(nodeConfiguration.isValid)

        assertEquals(listOf(fullPath / "cordapps"), nodeConfiguration.value().cordappDirectories)
    }

    @Test(timeout=6_000)
	fun `missing rpcSettings_adminAddress cause a graceful failure`() {
        val config = rawConfig.withoutPath("rpcSettings.adminAddress")

        val nodeConfiguration = config.parseAsNodeConfiguration()

        assertThat(nodeConfiguration.errors.asSequence().map(Configuration.Validation.Error::message).filter { it.contains("rpcSettings.adminAddress") }.toList()).isNotEmpty
    }

    @Test(timeout=6_000)
	fun `compatibilityZoneURL populates NetworkServices`() {
        val compatibilityZoneURL = URI.create("https://r3.example.com").toURL()
        val configuration = testConfiguration.copy(
                devMode = false,
                compatibilityZoneURL = compatibilityZoneURL)

        assertNotNull(configuration.networkServices)
        assertEquals(compatibilityZoneURL, configuration.networkServices!!.doormanURL)
        assertEquals(compatibilityZoneURL, configuration.networkServices!!.networkMapURL)
    }

    @Test(timeout=6_000)
	fun `jmxReporterType is null and defaults to Jokolia`() {
        val rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("devMode" to true)))
        val nodeConfig = rawConfig.parseAsNodeConfiguration().value()
        assertEquals(JmxReporterType.JOLOKIA.toString(), nodeConfig.jmxReporterType.toString())
    }

    @Test(timeout=6_000)
	fun `jmxReporterType is not null and is set to New Relic`() {
        var rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("devMode" to true)))
        rawConfig = rawConfig.withValue("jmxReporterType", ConfigValueFactory.fromAnyRef("NEW_RELIC"))
        val nodeConfig = rawConfig.parseAsNodeConfiguration().value()
        assertEquals(JmxReporterType.NEW_RELIC.toString(), nodeConfig.jmxReporterType.toString())
    }

    @Test(timeout=6_000)
	fun `jmxReporterType is not null and set to Jokolia`() {
        var rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("devMode" to true)))
        rawConfig = rawConfig.withValue("jmxReporterType", ConfigValueFactory.fromAnyRef("JOLOKIA"))
        val nodeConfig = rawConfig.parseAsNodeConfiguration().value()
        assertEquals(JmxReporterType.JOLOKIA.toString(), nodeConfig.jmxReporterType.toString())
    }

    @Test(timeout=6_000)
	fun `network services`() {
        val rawConfig = getConfig("test-config-with-networkservices.conf")
        val nodeConfig = rawConfig.parseAsNodeConfiguration().value()
        nodeConfig.networkServices!!.apply {
            assertEquals("https://registration.example.com", doormanURL.toString())
            assertEquals("https://cz.example.com", networkMapURL.toString())
            assertEquals("3c23d1a1-aa63-4beb-af9f-c8579dd5f89c", pnm.toString())
            assertEquals("my-TOKEN", csrToken)
        }
    }

    @Test(timeout=6_000)
    fun `check crlCheckArtemisServer flag`() {
        assertFalse(getConfig("working-config.conf").parseAsNodeConfiguration().value().crlCheckArtemisServer)
        val rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("crlCheckArtemisServer" to true)))
        assertTrue(rawConfig.parseAsNodeConfiguration().value().crlCheckArtemisServer)
    }

    @Test(timeout=6_000)
    fun `absolute network parameters path is set as specified by node config`() {
        val nodeConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("networkParametersPath" to tempFolder.root.canonicalPath))).parseAsNodeConfiguration().value()
        assertEquals(nodeConfig.networkParametersPath, tempFolder.root.toPath())
    }

    @Test(timeout=6_000)
    fun `relative network parameters path is set as specified by node config`() {
        val path = tempFolder.root.relativeTo(tempFolder.root.parentFile).toString()
        val fullPath = File(".").resolve(path).toString()
        val nodeConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("networkParametersPath" to fullPath))).parseAsNodeConfiguration().value()
        assertEquals(nodeConfig.networkParametersPath, nodeConfig.baseDirectory.resolve(fullPath))
    }

    @Test(timeout=6_000)
    fun `network parameters path is set as specified by node config with overridden base directory`() {
        val finalConfig = configOf(
                "baseDirectory" to "/path-to-base-directory",
                "networkParametersPath" to "/network")
                .withFallback(rawConfig)
                .resolve()
        val nodeConfig = finalConfig.parseAsNodeConfiguration().value()
        assertEquals(nodeConfig.networkParametersPath, Paths.get("/network"))
    }

    @Test(timeout=6_000)
    fun `network parameters path defaults to base directory`() {
        val nodeConfig = getConfig("working-config.conf").parseAsNodeConfiguration().value()
        assertEquals(nodeConfig.networkParametersPath, nodeConfig.baseDirectory)
    }

    @Test(timeout=6_000)
    fun `network parameters path defaults to overridden base directory`() {
        val finalConfig = configOf(
                "baseDirectory" to "/path-to-base-directory")
                .withFallback(rawConfig)
                .resolve()
        val nodeConfig = finalConfig.parseAsNodeConfiguration().value()
        assertEquals(nodeConfig.networkParametersPath, Paths.get("/path-to-base-directory"))
    }

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?): NodeConfigurationImpl {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private fun configTlsCertCrlOptions(tlsCertCrlDistPoint: URL?, tlsCertCrlIssuer: String?, crlCheckSoftFail: Boolean = true): NodeConfigurationImpl {
        return testConfiguration.copy(tlsCertCrlDistPoint = tlsCertCrlDistPoint, tlsCertCrlIssuer = tlsCertCrlIssuer?.let { X500Principal(it) }, crlCheckSoftFail = crlCheckSoftFail)
    }

    private val testConfiguration = testNodeConfiguration()

    private val rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
    private val rawConfigNoCordapps = ConfigFactory.parseResources("working-config-no-cordapps.conf", ConfigParseOptions.defaults().setAllowMissing(false))

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
                telemetry = TelemetryConfiguration(openTelemetryEnabled = true, simpleLogTelemetryEnabled = false, spanStartEndEventsEnabled = false, copyBaggageToTags = false),
                notary = null,
                devMode = true,
                noLocalShell = false,
                rpcSettings = rpcSettings,
                crlCheckSoftFail = true,
                tlsCertCrlDistPoint = null,
                flowOverrides = FlowOverrideConfig(listOf()),
                configurationWithOptions = mock()
        )
    }
}

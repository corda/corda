package net.corda.node.services.config

import com.typesafe.config.*
import com.zaxxer.hikari.HikariConfig
import net.corda.common.configuration.parsing.internal.ConfigObfuscator
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.core.internal.div
import net.corda.core.internal.toPath
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.config.getBooleanCaseInsensitive
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.persistence.CordaPersistence.DataSourceConfigTag
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.tools.shell.SSHDConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.Proxy
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `check SQLServer unicode check`() {
        val dataSourceProperties = Properties()
        dataSourceProperties[DataSourceConfigTag.DATA_SOURCE_URL] = "jdbc:sqlserver://localhost:10433;databaseName=perftesting;sendStringParametersAsUnicode=false"
        assertEquals("jdbc:sqlserver://localhost:10433;databaseName=perftesting;sendStringParametersAsUnicode=false", testConfiguration(dataSourceProperties).dataSourceProperties.getProperty(DataSourceConfigTag.DATA_SOURCE_URL))

        dataSourceProperties[DataSourceConfigTag.DATA_SOURCE_URL] = "jdbc:sqlserver://localhost:10433;databaseName=perftesting"
        assertEquals("jdbc:sqlserver://localhost:10433;databaseName=perftesting;sendStringParametersAsUnicode=false", testConfiguration(dataSourceProperties).dataSourceProperties.getProperty(DataSourceConfigTag.DATA_SOURCE_URL))

        dataSourceProperties[DataSourceConfigTag.DATA_SOURCE_URL] = "jdbc:sqlserver://localhost:10433;databaseName=perftesting;sendStringParametersAsUnicode=true"
        assertEquals("jdbc:sqlserver://localhost:10433;databaseName=perftesting;sendStringParametersAsUnicode=true", testConfiguration(dataSourceProperties).dataSourceProperties.getProperty(DataSourceConfigTag.DATA_SOURCE_URL))

        dataSourceProperties[DataSourceConfigTag.DATA_SOURCE_URL] = "jdbc:h2:///some/dir/persistence"
        assertEquals("jdbc:h2:///some/dir/persistence", testConfiguration(dataSourceProperties).dataSourceProperties.getProperty(DataSourceConfigTag.DATA_SOURCE_URL))

        assertNull(testConfiguration(Properties()).dataSourceProperties[DataSourceConfigTag.DATA_SOURCE_URL])
    }

    @Test
    fun `create hikari data source config`() {
        val dataSourceProperties = Properties()
        dataSourceProperties[DataSourceConfigTag.DATA_SOURCE_URL] = "jdbc:sqlserver://localhost:10433;databaseName=perftesting"
        val testConf = testConfiguration(dataSourceProperties)
        assertEquals("jdbc:sqlserver://localhost:10433;databaseName=perftesting;sendStringParametersAsUnicode=false", testConf.dataSourceProperties.getProperty(DataSourceConfigTag.DATA_SOURCE_URL))
        HikariConfig(testConf.dataSourceProperties)
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
    fun `mutual exclusion machineName set to default if not explicitly set`() {
        val config = getConfig("test-config-mutualExclusion-noMachineName.conf").parseAsNodeConfiguration(options = Configuration.Options(strict = false)).value()
        assertEquals(InetAddress.getLocalHost().hostName, config.enterpriseConfiguration.mutualExclusionConfiguration.machineName)
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
    fun `test reading proxy configuration`() {
        val configuration = testConfiguration.copy(
                devMode = false,
                networkServices = NetworkServicesConfig(
                        URL("https://r3.com.doorman"),
                        URL("https://r3.com/nm"),
                        proxyType = Proxy.Type.HTTP,
                        proxyAddress = NetworkHostAndPort("localhost", 8080),
                        proxyUser = "user",
                        proxyPassword = "password"
                ))

        val errors = configuration.validate()
        assertThat(errors).isEmpty()
    }

    @Test
    fun `can't specify proxy type without address`() {
        val configuration = testConfiguration.copy(
                devMode = false,
                networkServices = NetworkServicesConfig(
                        URL("https://r3.com.doorman"),
                        URL("https://r3.com/nm"),
                        proxyType = Proxy.Type.HTTP
                ))

        val errors = configuration.validate()
        assertThat(errors).contains("cannot enable network proxy by specifying 'networkServices.proxyType' without providing 'networkServices.proxyAddress'")
    }

    @Test
    fun `validation has error on non-null cryptoServiceConf for null cryptoServiceName`() {
        val configuration = testConfiguration.copy(cryptoServiceConf = File("unsupported.conf").toPath())

        val errors = configuration.validate()

        assertThat(errors).hasOnlyOneElementSatisfying {
            error -> error.contains("cryptoServiceName is null, but cryptoServiceConf is set to unsupported.conf")
        }
    }

    @Test
    fun `fail on wrong cryptoServiceName`() {
        var rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
        rawConfig = rawConfig.withValue("cryptoServiceName", ConfigValueFactory.fromAnyRef("UNSUPPORTED"))

        val config = rawConfig.parseAsNodeConfiguration()

        assertThat(config.errors.asSequence().map(Configuration.Validation.Error::message).filter { it.contains("has no constant of the name 'UNSUPPORTED'") }.toList()).isNotEmpty
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

    @Test
    fun `can deobfuscate config fields`() {
        val rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("emailAddress" to "<{7JW92M2zxMtf8LVhHA3B1Q==:nGBvd90AdSs7psEJqabBURvbpggPLBqQIFqTPthoU3il}>")))
        val nodeConfig = rawConfig.parseAsNodeConfiguration(Configuration.Options(strict = true, hardwareAddress = byteArrayOf(0, 0, 0, 0, 0, 0), seed = byteArrayOf(0))).value()
        assertEquals("admin@company.com", nodeConfig.emailAddress)
    }

    @Test(expected = ConfigObfuscator.DeobfuscationFailedForPathException::class)
    fun `cannot deobfuscate config fields without the right hardware address`() {
        val rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("emailAddress" to "<{7JW92M2zxMtf8LVhHA3B1Q==:nGBvd90AdSs7psEJqabBURvbpggPLBqQIFqTPthoU3il}>")))
        val nodeConfig = rawConfig.parseAsNodeConfiguration(Configuration.Options(strict = true, hardwareAddress = byteArrayOf(1, 2, 3, 4, 5, 6), seed = byteArrayOf(0))).value()
        assertEquals("admin@company.com", nodeConfig.emailAddress)
    }

    @Test
    fun `can deobfuscate config fields in nested objects`() {
        val rawConfig = getConfig("working-config.conf", ConfigFactory.parseMap(mapOf("dataSourceProperties.dataSource.password" to "<{I+/c+bIYfIrhxjyP0ANK6Q==:7f46ICS1hogaB3Vfaz47xCH6zgI=}>")))
        val nodeConfig = rawConfig.parseAsNodeConfiguration(Configuration.Options(strict = true, hardwareAddress = byteArrayOf(0, 0, 0, 0, 0, 0), seed = byteArrayOf(0))).value()
        assertEquals("demo", nodeConfig.dataSourceProperties["dataSource.password"])
    }

    @Test
    fun `can configure a notary with an hsm`() {
        val rawConfig = getConfig("notary-config.conf", ConfigFactory.parseMap(mapOf("cryptoServiceName" to "AZURE_KEY_VAULT")))
        val nodeConfig = rawConfig.parseAsNodeConfiguration(Configuration.Options(strict = true, hardwareAddress = byteArrayOf(0, 0, 0, 0, 0, 0), seed = byteArrayOf(0))).value()
        assertEquals(SupportedCryptoServices.AZURE_KEY_VAULT, nodeConfig.cryptoServiceName)
    }

    @Test
    fun `can successfully read artemis crypto service config`() {
        val rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
        val nodeConfig = rawConfig.parseAsNodeConfiguration()
        assertThat(nodeConfig.isValid).isTrue()
        assertEquals(SupportedCryptoServices.AZURE_KEY_VAULT, nodeConfig.value().enterpriseConfiguration.artemisCryptoServiceConfig?.name)
        assertEquals( Paths.get("./azure.conf"), nodeConfig.value().enterpriseConfiguration.artemisCryptoServiceConfig?.conf)
    }

    @Test
    fun `error on wrong crypto service name `() {
        var rawConfig = ConfigFactory.parseResources("working-config.conf", ConfigParseOptions.defaults().setAllowMissing(false))
        rawConfig = rawConfig.withValue("enterpriseConfiguration.artemisCryptoServiceConfig.cryptoServiceName", ConfigValueFactory.fromAnyRef("UNSUPPORTED"))
        val nodeConfig = rawConfig.parseAsNodeConfiguration()
        assertThat(nodeConfig.errors.asSequence().map(Configuration.Validation.Error::message).filter
            { it.contains("The enum class SupportedCryptoServices has no constant of the name 'UNSUPPORTED'") }.toList()).isNotEmpty
    }

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?): NodeConfigurationImpl {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private fun configTlsCertCrlOptions(tlsCertCrlDistPoint: URL?, tlsCertCrlIssuer: String?, crlCheckSoftFail: Boolean = true): NodeConfigurationImpl {
        return testConfiguration.copy(tlsCertCrlDistPoint = tlsCertCrlDistPoint, tlsCertCrlIssuer = tlsCertCrlIssuer?.let { X500Principal(it) }, crlCheckSoftFail = crlCheckSoftFail)
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
                flowTimeout = FlowTimeoutConfiguration(5.seconds, 3, 1.0),
                notary = null,
                devMode = true,
                noLocalShell = false,
                rpcSettings = rpcSettings,
                relay = null,
                enterpriseConfiguration = EnterpriseConfiguration((MutualExclusionConfiguration(false, "", 20000, 40000))),
                crlCheckSoftFail = true,
                tlsCertCrlDistPoint = null,
                flowOverrides = FlowOverrideConfig(listOf())
        )
    }
}

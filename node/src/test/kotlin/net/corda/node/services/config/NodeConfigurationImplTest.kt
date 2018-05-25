/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import net.corda.core.internal.toPath
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.persistence.CordaPersistence.DataSourceConfigTag
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.tools.shell.SSHDConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.InetAddress
import java.net.URL
import java.net.URI
import java.nio.file.Paths
import java.util.*
import kotlin.test.*

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

    @Test
    fun `mutual exclusion machineName set to default if not explicitly set`() {
        val config = getConfig("test-config-mutualExclusion-noMachineName.conf").parseAsNodeConfiguration(UnknownConfigKeysPolicy.IGNORE::handle)
        assertEquals(InetAddress.getLocalHost().hostName, config.enterpriseConfiguration.mutualExclusionConfiguration.machineName)
    }

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?): NodeConfiguration {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private fun configTlsCertCrlOptions(tlsCertCrlDistPoint: URL?, tlsCertCrlIssuer: String?, crlCheckSoftFail: Boolean = true): NodeConfiguration {
        return testConfiguration.copy(tlsCertCrlDistPoint = tlsCertCrlDistPoint, tlsCertCrlIssuer = tlsCertCrlIssuer, crlCheckSoftFail = crlCheckSoftFail)
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
                relay = null,
                enterpriseConfiguration = EnterpriseConfiguration((MutualExclusionConfiguration(false, "", 20000, 40000))),
                crlCheckSoftFail = true,
                tlsCertCrlDistPoint = null
        )
    }
}

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

import com.zaxxer.hikari.HikariConfig
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.persistence.CordaPersistence.DataSourceConfigTag
import net.corda.core.utilities.seconds
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.tools.shell.SSHDConfiguration
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.nio.file.Paths
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
                relay = null,
                enterpriseConfiguration = EnterpriseConfiguration((MutualExclusionConfiguration(false, "", 20000, 40000))),
                crlCheckSoftFail = true
        )
    }
}

package net.corda.node.services.config

import com.zaxxer.hikari.HikariConfig
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.persistence.CordaPersistence.DataSourceConfigTag
import net.corda.testing.ALICE_NAME
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
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

    private fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?) : NodeConfiguration {
        return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
    }

    private fun testConfiguration(dataSourceProperties: Properties): NodeConfigurationImpl {
        return testConfiguration.copy(dataSourceProperties = dataSourceProperties)
    }

    private val testConfiguration = NodeConfigurationImpl(
            baseDirectory = Paths.get("."),
            myLegalName = ALICE_NAME,
            emailAddress = "",
            keyStorePassword = "cordacadevpass",
            trustStorePassword = "trustpass",
            dataSourceProperties = makeTestDataSourceProperties(ALICE_NAME.organisation),
            rpcUsers = emptyList(),
            verifierType = VerifierType.InMemory,
            p2pAddress = NetworkHostAndPort("localhost", 0),
            rpcAddress = NetworkHostAndPort("localhost", 1),
            messagingServerAddress = null,
            notary = null,
            certificateChainCheckPolicies = emptyList(),
            devMode = true,
            activeMQServer = ActiveMqServerConfiguration(BridgeConfiguration(0, 0, 0.0)),
            relay = null,
            enterpriseConfiguration = EnterpriseConfiguration((MutualExclusionConfiguration(false, "", 20000, 40000))))
}

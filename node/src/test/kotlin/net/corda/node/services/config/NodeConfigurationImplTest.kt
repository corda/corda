package net.corda.node.services.config

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.User
import net.corda.testing.ALICE
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseProperties
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.URL
import java.nio.file.Paths
import java.util.*
import kotlin.test.assertFalse

class NodeConfigurationImplTest {
    @Test
    fun `Can't have dev mode options if not in dev mode`() {
        val testConfiguration = NodeConfigurationImpl(
                baseDirectory = Paths.get("."),
                myLegalName = ALICE.name,
                networkMapService = null,
                emailAddress = "",
                keyStorePassword = "cordacadevpass",
                trustStorePassword = "trustpass",
                dataSourceProperties = makeTestDataSourceProperties(ALICE.name.organisation),
                database = makeTestDatabaseProperties(),
                certificateSigningService = URL("http://localhost"),
                rpcUsers = emptyList(),
                verifierType = VerifierType.InMemory,
                useHTTPS = false,
                p2pAddress = NetworkHostAndPort("localhost", 0),
                rpcAddress = NetworkHostAndPort("localhost", 1),
                messagingServerAddress = null,
                notary = null,
                certificateChainCheckPolicies = emptyList(),
                devMode = true,
                activeMQServer = ActiveMqServerConfiguration(BridgeConfiguration(0, 0, 0.0)),
                additionalNodeInfoPollingFrequencyMsec = 5.seconds.toMillis())

        fun configDebugOptions(devMode: Boolean, debugOptions: DevModeOptions?) {
            testConfiguration.copy(devMode = devMode, devModeOptions = debugOptions)
        }
        val debugOptions = DevModeOptions(null)
        configDebugOptions(true, debugOptions)
        configDebugOptions(true,null)
        assertThatThrownBy{configDebugOptions(false, debugOptions)}.hasMessageMatching( "Cannot use devModeOptions outside of dev mode" )
        configDebugOptions(false,null)
    }

    @Test
    fun `check devModeOptions flag helper`()
    {
        val testConfiguration = NodeConfigurationImpl(
                baseDirectory = Paths.get("."),
                myLegalName = ALICE.name,
                networkMapService = null,
                emailAddress = "",
                keyStorePassword = "cordacadevpass",
                trustStorePassword = "trustpass",
                dataSourceProperties = makeTestDataSourceProperties(ALICE.name.organisation),
                database = makeTestDatabaseProperties(),
                certificateSigningService = URL("http://localhost"),
                rpcUsers = emptyList(),
                verifierType = VerifierType.InMemory,
                useHTTPS = false,
                p2pAddress = NetworkHostAndPort("localhost", 0),
                rpcAddress = NetworkHostAndPort("localhost", 1),
                messagingServerAddress = null,
                notary = null,
                certificateChainCheckPolicies = emptyList(),
                devMode = true,
                activeMQServer = ActiveMqServerConfiguration(BridgeConfiguration(0, 0, 0.0)),
                additionalNodeInfoPollingFrequencyMsec = 5.seconds.toMillis())

        fun configDebugOptions(devMode: Boolean, devModeOptions: DevModeOptions?) : NodeConfiguration {
            return testConfiguration.copy(devMode = devMode, devModeOptions = devModeOptions)
        }
        assertFalse { configDebugOptions(true,null).devModeOptions?.disableCheckpointChecker == true}
        assertFalse { configDebugOptions(true,DevModeOptions(null)).devModeOptions?.disableCheckpointChecker == true}
        assertFalse { configDebugOptions(true,DevModeOptions(false)).devModeOptions?.disableCheckpointChecker == true}
        assert ( configDebugOptions(true,DevModeOptions(true)).devModeOptions?.disableCheckpointChecker == true)
    }

}

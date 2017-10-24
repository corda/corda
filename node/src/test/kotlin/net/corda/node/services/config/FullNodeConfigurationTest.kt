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

class FullNodeConfigurationTest {
    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        val testConfiguration = FullNodeConfiguration(
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

        fun configWithRPCUsername(username: String) {
            testConfiguration.copy(rpcUsers = listOf(User(username, "pass", emptySet())))
        }
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }

    @Test
    fun `Can't have debug options if not in dev mode`() {
        val testConfiguration = FullNodeConfiguration(
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

        fun configDebugOptions(devMode: Boolean, debugOptions: Properties?) {
            testConfiguration.copy(devMode = devMode, debugOptions = debugOptions)
        }
        val debugOptions = Properties()
        configDebugOptions(true, debugOptions)
        configDebugOptions(true,null)
        assertThatThrownBy{configDebugOptions(false, debugOptions)}.hasMessageMatching("Cannot use debugOptions outside of dev mode")
        configDebugOptions(false,null)
    }

    @Test
    fun `check properties behave as expected`()
    {
        val testConfiguration = FullNodeConfiguration(
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

        fun configDebugOptions(devMode: Boolean, debugOptions: Properties?) : NodeConfiguration {
            return testConfiguration.copy(devMode = devMode, debugOptions = debugOptions)
        }
        val debugOptions = Properties()
        assertFalse { configDebugOptions(true, debugOptions).debugOptions?.getProperty("foo") == "bar"}
        assertFalse { configDebugOptions(true,null).debugOptions?.getProperty("foo") == "bar"}
        debugOptions.setProperty("foo", "bar")
        assert( configDebugOptions(true, debugOptions).debugOptions?.getProperty("foo") == "bar")
    }

}

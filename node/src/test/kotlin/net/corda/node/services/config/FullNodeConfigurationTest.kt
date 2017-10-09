package net.corda.node.services.config

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.User
import net.corda.testing.ALICE
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseProperties
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.URL
import java.nio.file.Paths

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
                extraAdvertisedServiceIds = emptyList(),
                notary = null,
                certificateChainCheckPolicies = emptyList(),
                devMode = true,
                activeMQServer = ActiveMqServerConfiguration(BridgeConfiguration(0, 0, 0.0)))

        fun configWithRPCUsername(username: String) {
            testConfiguration.copy(rpcUsers = listOf(User(username, "pass", emptySet())))
        }
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }
}

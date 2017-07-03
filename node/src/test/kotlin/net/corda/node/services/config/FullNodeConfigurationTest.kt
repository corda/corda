package net.corda.node.services.config

import net.corda.core.crypto.commonName
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.Authority
import net.corda.nodeapi.User
import net.corda.testing.node.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.URL
import java.nio.file.Paths

class FullNodeConfigurationTest {
    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        val testConfiguration = FullNodeConfiguration(
                basedir = Paths.get("."),
                myLegalName = ALICE.name,
                networkMapService = null,
                emailAddress = "",
                keyStorePassword = "cordacadevpass",
                trustStorePassword = "trustpass",
                dataSourceProperties = makeTestDataSourceProperties(ALICE.name.commonName),
                certificateSigningService = URL("http://localhost"),
                rpcUsers = emptyList(),
                verifierType = VerifierType.InMemory,
                useHTTPS = false,
                p2pAddress = Authority("localhost", 0),
                rpcAddress = Authority("localhost", 1),
                messagingServerAddress = null,
                extraAdvertisedServiceIds = emptyList(),
                bftReplicaId = null,
                notaryNodeAddress = null,
                notaryClusterAddresses = emptyList(),
                certificateChainCheckPolicies = emptyList(),
                devMode = true)

        fun configWithRPCUsername(username: String) {
            testConfiguration.copy(rpcUsers = listOf(User(username, "pass", emptySet())))
        }
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }
}

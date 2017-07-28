package net.corda.node.services.config

import net.corda.core.crypto.commonName
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.ALICE
import net.corda.nodeapi.User
import net.corda.testing.node.makeTestDataSourceProperties
import net.corda.testing.node.testDbTransactionIsolationLevel
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
                dbTransactionIsolationLevel = testDbTransactionIsolationLevel(),
                certificateSigningService = URL("http://localhost"),
                rpcUsers = emptyList(),
                verifierType = VerifierType.InMemory,
                useHTTPS = false,
                p2pAddress = NetworkHostAndPort("localhost", 0),
                rpcAddress = NetworkHostAndPort("localhost", 1),
                messagingServerAddress = null,
                extraAdvertisedServiceIds = emptyList(),
                bftSMaRt = BFTSMaRtConfiguration(-1, false),
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

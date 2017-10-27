package net.corda.node.services.config

import com.typesafe.config.ConfigFactory
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.testing.ALICE
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import net.corda.testing.node.MockServices.Companion.makeTestDatabaseProperties
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class NodeConfigurationTest {
    @Test
    fun `Artemis special characters not permitted in RPC usernames`() {
        val map = mapOf<String, Any?>(
                "baseDirectory" to "",
                "myLegalName" to ALICE.name.toString(),
                "networkMapService" to null,
                "emailAddress" to "",
                "keyStorePassword" to "cordacadevpass",
                "trustStorePassword" to "trustpass",
                "dataSourceProperties" to makeTestDataSourceProperties(ALICE.name.organisation),
                "database" to makeTestDatabaseProperties(),
                "verifierType" to VerifierType.InMemory.toString(),
                "useHTTPS" to false,
                "p2pAddress" to NetworkHostAndPort("localhost", 0).toString(),
                "rpcAddress" to NetworkHostAndPort("localhost", 1).toString(),
                "messagingServerAddress" to null,
                "notary" to null,
                "certificateChainCheckPolicies" to emptyList<Any>(),
                "devMode" to true,
                "additionalNodeInfoPollingFrequencyMsec" to 5.seconds.toMillis())


        fun configWithRPCUsername(username: String) {
            ConfigFactory.parseMap(map)
                    .plus(mapOf("rpcUsers" to listOf(net.corda.nodeapi.User(username, "pass", emptySet()))))
                    .parseAsNodeConfiguration()
        }
        assertThatThrownBy { configWithRPCUsername("user.1") }.hasMessageContaining(".")
        assertThatThrownBy { configWithRPCUsername("user*1") }.hasMessageContaining("*")
        assertThatThrownBy { configWithRPCUsername("user#1") }.hasMessageContaining("#")
    }
}

package net.corda.services.messaging

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.support.jdk7.use
import net.corda.core.*
import net.corda.core.crypto.Party
import net.corda.core.node.NodeInfo
import net.corda.flows.sendRequest
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.network.NetworkMapService.Companion.REGISTER_FLOW_TOPIC
import net.corda.node.services.network.NetworkMapService.RegistrationRequest
import net.corda.node.services.network.NodeRegistration
import net.corda.node.utilities.AddOrRemove
import net.corda.testing.TestNodeConfiguration
import net.corda.testing.node.NodeBasedTest
import net.corda.testing.node.SimpleNode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeoutException

class P2PSecurityTest : NodeBasedTest() {

    @Test
    fun `incorrect legal name for the network map service config`() {
        val incorrectNetworkMapName = random63BitValue().toString()
        val node = startNode("Bob", configOverrides = mapOf(
                "networkMapService" to mapOf(
                        "address" to networkMapNode.configuration.artemisAddress.toString(),
                        "legalName" to incorrectNetworkMapName
                )
        ))
        // The connection will be rejected as the legal name doesn't match
        assertThatThrownBy { node.getOrThrow() }.hasMessageContaining(incorrectNetworkMapName)
    }

    @Test
    fun `register with the network map service using a legal name different from the TLS CN`() {
        startSimpleNode("Attacker").use {
            // Register with the network map using a different legal name
            val response = it.registerWithNetworkMap("Legit Business")
            // We don't expect a response because the network map's host verification will prevent a connection back
            // to the attacker as the TLS CN will not match the legal name it has just provided
            assertThatExceptionOfType(TimeoutException::class.java).isThrownBy {
                response.getOrThrow(2.seconds)
            }
        }
    }

    private fun startSimpleNode(legalName: String): SimpleNode {
        val config = TestNodeConfiguration(
                baseDirectory = tempFolder.root.toPath() / legalName,
                myLegalName = legalName,
                networkMapService = NetworkMapInfo(networkMapNode.configuration.artemisAddress, networkMapNode.info.legalIdentity.name))
        config.configureWithDevSSLCertificate() // This creates the node's TLS cert with the CN as the legal name
        return SimpleNode(config).apply { start() }
    }

    private fun SimpleNode.registerWithNetworkMap(registrationName: String): ListenableFuture<NetworkMapService.RegistrationResponse> {
        val nodeInfo = NodeInfo(net.myAddress, Party(registrationName, identity.public))
        val registration = NodeRegistration(nodeInfo, System.currentTimeMillis(), AddOrRemove.ADD, Instant.MAX)
        val request = RegistrationRequest(registration.toWire(identity.private), net.myAddress)
        return net.sendRequest<NetworkMapService.RegistrationResponse>(REGISTER_FLOW_TOPIC, request, networkMapNode.net.myAddress)
    }
}
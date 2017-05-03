package net.corda.services.messaging

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.crypto.Party
import net.corda.core.crypto.commonName
import net.corda.core.div
import net.corda.core.getOrThrow
import net.corda.core.node.NodeInfo
import net.corda.core.random63BitValue
import net.corda.core.seconds
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.flows.sendRequest
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.network.NetworkMapService.RegistrationRequest
import net.corda.node.services.network.NodeRegistration
import net.corda.node.utilities.AddOrRemove
import net.corda.testing.MOCK_VERSION_INFO
import net.corda.testing.TestNodeConfiguration
import net.corda.testing.node.NodeBasedTest
import net.corda.testing.node.SimpleNode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeoutException

class P2PSecurityTest : NodeBasedTest() {

    @Test
    fun `incorrect legal name for the network map service config`() {
        val incorrectNetworkMapName = random63BitValue().toString()
        val node = startNode(BOB.name, configOverrides = mapOf(
                "networkMapService" to mapOf(
                        "address" to networkMapNode.configuration.p2pAddress.toString(),
                        "legalName" to incorrectNetworkMapName
                )
        ))
        // The connection will be rejected as the legal name doesn't match
        assertThatThrownBy { node.getOrThrow() }.hasMessageContaining(incorrectNetworkMapName)
    }

    @Test
    fun `register with the network map service using a legal name different from the TLS CN`() {
        startSimpleNode(DUMMY_BANK_A.name).use {
            // Register with the network map using a different legal name
            val response = it.registerWithNetworkMap(DUMMY_BANK_B.name)
            // We don't expect a response because the network map's host verification will prevent a connection back
            // to the attacker as the TLS CN will not match the legal name it has just provided
            assertThatExceptionOfType(TimeoutException::class.java).isThrownBy {
                response.getOrThrow(2.seconds)
            }
        }
    }

    private fun startSimpleNode(legalName: X500Name): SimpleNode {
        val config = TestNodeConfiguration(
                baseDirectory = tempFolder.root.toPath() / legalName.commonName,
                myLegalName = legalName,
                networkMapService = NetworkMapInfo(networkMapNode.configuration.p2pAddress, networkMapNode.info.legalIdentity.name))
        config.configureWithDevSSLCertificate() // This creates the node's TLS cert with the CN as the legal name
        return SimpleNode(config).apply { start() }
    }

    private fun SimpleNode.registerWithNetworkMap(registrationName: X500Name): ListenableFuture<NetworkMapService.RegistrationResponse> {
        val nodeInfo = NodeInfo(net.myAddress, Party(registrationName, identity.public), MOCK_VERSION_INFO.platformVersion)
        val registration = NodeRegistration(nodeInfo, System.currentTimeMillis(), AddOrRemove.ADD, Instant.MAX)
        val request = RegistrationRequest(registration.toWire(identity.private), net.myAddress)
        return net.sendRequest<NetworkMapService.RegistrationResponse>(NetworkMapService.REGISTER_TOPIC, request, networkMapNode.net.myAddress)
    }
}

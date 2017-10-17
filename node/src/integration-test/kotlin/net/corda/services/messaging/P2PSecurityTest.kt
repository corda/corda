package net.corda.services.messaging

import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NodeInfo
import net.corda.core.internal.cert
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.services.config.ActiveMqServerConfiguration
import net.corda.node.services.config.BridgeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.network.NetworkMapService.RegistrationRequest
import net.corda.node.services.network.NodeRegistration
import net.corda.node.utilities.AddOrRemove
import net.corda.testing.*
import net.corda.testing.node.NodeBasedTest
import net.corda.testing.node.SimpleNode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.concurrent.TimeoutException

class P2PSecurityTest : NodeBasedTest() {

    @Test
    fun `incorrect legal name for the network map service config`() {
        val incorrectNetworkMapName = CordaX500Name(organisation = "NetworkMap-${random63BitValue()}",
                locality = "London", country = "GB")
        val node = startNode(BOB.name, configOverrides = mapOf(
                "networkMapService" to mapOf(
                        "address" to networkMapNode.internals.configuration.p2pAddress.toString(),
                        "legalName" to incorrectNetworkMapName.toString()
                )
        ))
        // The connection will be rejected as the legal name doesn't match
        assertThatThrownBy { node.getOrThrow() }.hasMessageContaining(incorrectNetworkMapName.toString())
    }

    @Test
    fun `register with the network map service using a legal name different from the TLS CN`() {
        startSimpleNode(DUMMY_BANK_A.name, DEV_TRUST_ROOT.cert).use {
            // Register with the network map using a different legal name
            val response = it.registerWithNetworkMap(DUMMY_BANK_B.name)
            // We don't expect a response because the network map's host verification will prevent a connection back
            // to the attacker as the TLS CN will not match the legal name it has just provided
            assertThatExceptionOfType(TimeoutException::class.java).isThrownBy {
                response.getOrThrow(2.seconds)
            }
        }
    }

    private fun startSimpleNode(legalName: CordaX500Name,
                                trustRoot: X509Certificate): SimpleNode {
        val config = testNodeConfiguration(
                baseDirectory = baseDirectory(legalName),
                myLegalName = legalName).also {
            whenever(it.networkMapService).thenReturn(NetworkMapInfo(networkMapNode.internals.configuration.p2pAddress, networkMapNode.info.chooseIdentity().name))
            whenever(it.activeMQServer).thenReturn(ActiveMqServerConfiguration(BridgeConfiguration(1001, 2, 3.4)))
        }
        config.configureWithDevSSLCertificate() // This creates the node's TLS cert with the CN as the legal name
        return SimpleNode(config, trustRoot = trustRoot).apply { start() }
    }

    private fun SimpleNode.registerWithNetworkMap(registrationName: CordaX500Name): CordaFuture<NetworkMapService.RegistrationResponse> {
        val legalIdentity = getTestPartyAndCertificate(registrationName, identity.public)
        val nodeInfo = NodeInfo(listOf(MOCK_HOST_AND_PORT), listOf(legalIdentity), 1, serial = 1)
        val registration = NodeRegistration(nodeInfo, System.currentTimeMillis(), AddOrRemove.ADD, Instant.MAX)
        val request = RegistrationRequest(registration.toWire(keyService, identity.public), network.myAddress)
        return network.sendRequest<NetworkMapService.RegistrationResponse>(NetworkMapService.REGISTER_TOPIC, request, networkMapNode.network.myAddress)
    }
}

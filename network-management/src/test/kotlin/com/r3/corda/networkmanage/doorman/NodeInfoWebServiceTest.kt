package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.common.utils.withCert
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash.Companion.randomSHA256
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.cert
import net.corda.core.internal.openHttpConnection
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException
import java.net.URL
import javax.ws.rs.core.MediaType
import kotlin.test.assertEquals

class NodeInfoWebServiceTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val rootCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val rootCaCert = X509Utilities.createSelfSignedCACertificate(CordaX500Name("Corda Node Root CA", "R3 LTD", "London", "GB"), rootCaKeyPair)
    private val intermediateCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    private val intermediateCaCert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, rootCaCert, rootCaKeyPair, X500Name("CN=Corda Node Intermediate CA,L=London"), intermediateCaKeyPair.public)
    private val testNetworkMapConfig =  NetworkMapConfig(10.seconds.toMillis(), 10.seconds.toMillis())

    @Test
    fun `submit nodeInfo`() {
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"))

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(mock(), mock(), testNetworkMapConfig)).use {
            it.start()
            val nodeInfoAndSignature = signedNodeInfo.serialize().bytes
            // Post node info and signature to doorman, this should pass without any exception.
            it.doPost("publish", nodeInfoAndSignature)
        }
    }

    @Test
    fun `get network map`() {
        val networkMap = NetworkMap(listOf(randomSHA256(), randomSHA256()), randomSHA256())
        val serializedNetworkMap = networkMap.serialize()
        val signedNetworkMap = SignedNetworkMap(serializedNetworkMap, intermediateCaKeyPair.sign(serializedNetworkMap).withCert(intermediateCaCert.cert))

        val networkMapStorage: NetworkMapStorage = mock {
            on { getCurrentNetworkMap() }.thenReturn(signedNetworkMap)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val signedNetworkMapResponse = it.doGet<SignedNetworkMap>("")
            verify(networkMapStorage, times(1)).getCurrentNetworkMap()
            assertEquals(signedNetworkMapResponse.verified(rootCaCert.cert), networkMap)
        }
    }

    @Test
    fun `get node info`() {
        val (nodeInfo, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"))
        val nodeInfoHash = nodeInfo.serialize().hash

        val nodeInfoStorage: NodeInfoStorage = mock {
            on { getNodeInfo(nodeInfoHash) }.thenReturn(signedNodeInfo)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(nodeInfoStorage, mock(), testNetworkMapConfig)).use {
            it.start()
            val nodeInfoResponse = it.doGet<SignedNodeInfo>("node-info/$nodeInfoHash")
            verify(nodeInfoStorage, times(1)).getNodeInfo(nodeInfoHash)
            assertEquals(nodeInfo, nodeInfoResponse.verified())

            assertThatExceptionOfType(FileNotFoundException::class.java).isThrownBy {
                it.doGet<SignedNodeInfo>("node-info/${randomSHA256()}")
            }
        }
    }

    @Test
    fun `get network parameters`() {
        val netParams = testNetworkParameters(emptyList())
        val serializedNetParams = netParams.serialize()
        val signedNetParams = SignedData(serializedNetParams, intermediateCaKeyPair.sign(serializedNetParams))
        val netParamsHash = serializedNetParams.hash

        val networkMapStorage: NetworkMapStorage = mock {
            on { getSignedNetworkParameters(netParamsHash) }.thenReturn(signedNetParams)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val netParamsResponse = it.doGet<SignedData<NetworkParameters>>("network-parameter/$netParamsHash")
            verify(networkMapStorage, times(1)).getSignedNetworkParameters(netParamsHash)
            assertThat(netParamsResponse.verified()).isEqualTo(netParams)
            assertThat(netParamsResponse.sig.by).isEqualTo(intermediateCaKeyPair.public)

            assertThatExceptionOfType(FileNotFoundException::class.java).isThrownBy {
                it.doGet<SignedData<NetworkParameters>>("network-parameter/${randomSHA256()}")
            }
        }
    }

    private fun NetworkManagementWebServer.doPost(path: String, payload: ByteArray) {
        val url = URL("http://$hostAndPort/network-map/$path")
        url.openHttpConnection().apply {
            doOutput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", MediaType.APPLICATION_OCTET_STREAM)
            outputStream.write(payload)
            inputStream.close() // This will give us a nice IOException if the response isn't HTTP 200
        }
    }

    private inline fun <reified T : Any> NetworkManagementWebServer.doGet(path: String): T {
        val url = URL("http://$hostAndPort/network-map/$path")
        return url.openHttpConnection().inputStream.use { it.readBytes().deserialize() }
    }
}
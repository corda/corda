package com.r3.corda.networkmanage.doorman.webservice

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.common.utils.SignedNetworkMap
import com.r3.corda.networkmanage.common.utils.SignedNetworkParameters
import com.r3.corda.networkmanage.doorman.NetworkManagementWebServer
import com.r3.corda.networkmanage.doorman.NetworkMapConfig
import net.corda.core.crypto.SecureHash.Companion.randomSHA256
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.checkOkResponse
import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.responseAs
import net.corda.core.internal.signWithCert
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.URL
import java.security.cert.X509Certificate
import javax.ws.rs.core.MediaType
import kotlin.test.assertEquals

class NetworkMapWebServiceTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private lateinit var rootCaCert: X509Certificate
    private lateinit var networkMapCa: CertificateAndKeyPair

    private val testNetworkMapConfig = NetworkMapConfig(10.seconds.toMillis(), 10.seconds.toMillis())

    @Before
    fun init() {
        val (rootCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        networkMapCa = createDevNetworkMapCa(rootCa)
    }

    @Test
    fun `submit nodeInfo`() {
        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkParametersOfNetworkMap() }.thenReturn(testNetworkParameters(emptyList()).signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate))
        }
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"))

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val nodeInfoAndSignature = signedNodeInfo.serialize().bytes
            // Post node info and signature to doorman, this should pass without any exception.
            it.doPost("publish", nodeInfoAndSignature)
        }
    }

    @Test
    fun `submit old nodeInfo`() {
        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkParametersOfNetworkMap() }.thenReturn(testNetworkParameters(emptyList(), minimumPlatformVersion = 2).signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate))
        }
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"), platformVersion = 1)

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val nodeInfoAndSignature = signedNodeInfo.serialize().bytes
            assertThatThrownBy { it.doPost("publish", nodeInfoAndSignature) }
                    .hasMessageStartingWith("Response Code 400: Minimum platform version is 2")
        }
    }

    @Test
    fun `submit nodeInfo when no network parameters`() {
        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkParametersOfNetworkMap() }.thenReturn(null)
        }
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"), platformVersion = 1)

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val nodeInfoAndSignature = signedNodeInfo.serialize().bytes
            assertThatThrownBy { it.doPost("publish", nodeInfoAndSignature) }
                    .hasMessageStartingWith("Response Code 503: Network parameters have not been initialised")
        }
    }

    @Test
    fun `get network map`() {
        val networkMap = NetworkMap(listOf(randomSHA256(), randomSHA256()), randomSHA256())
        val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)

        val networkMapStorage: NetworkMapStorage = mock {
            on { getCurrentNetworkMap() }.thenReturn(signedNetworkMap)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val signedNetworkMapResponse = it.doGet<SignedNetworkMap>("")
            verify(networkMapStorage, times(1)).getCurrentNetworkMap()
            assertEquals(signedNetworkMapResponse.verifiedNetworkMapCert(rootCaCert), networkMap)
        }
    }

    @Test
    fun `get node info`() {
        val (nodeInfo, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"))
        val nodeInfoHash = nodeInfo.serialize().hash

        val nodeInfoStorage: NodeInfoStorage = mock {
            on { getNodeInfo(nodeInfoHash) }.thenReturn(signedNodeInfo)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(nodeInfoStorage, mock(), testNetworkMapConfig)).use {
            it.start()
            val nodeInfoResponse = it.doGet<SignedNodeInfo>("node-info/$nodeInfoHash")
            verify(nodeInfoStorage, times(1)).getNodeInfo(nodeInfoHash)
            assertEquals(nodeInfo, nodeInfoResponse.verified())

            assertThatExceptionOfType(IOException::class.java)
                    .isThrownBy { it.doGet<SignedNodeInfo>("node-info/${randomSHA256()}") }
                    .withMessageContaining("404")
        }
    }

    @Test
    fun `get network parameters`() {
        val networkParameters = testNetworkParameters(emptyList())
        val signedNetworkParameters = networkParameters.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
        val networkParametersHash = signedNetworkParameters.raw.hash

        val networkMapStorage: NetworkMapStorage = mock {
            on { getSignedNetworkParameters(networkParametersHash) }.thenReturn(signedNetworkParameters)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val netParamsResponse = it.doGet<SignedNetworkParameters>("network-parameters/$networkParametersHash")
            verify(networkMapStorage, times(1)).getSignedNetworkParameters(networkParametersHash)
            assertThat(netParamsResponse.verified()).isEqualTo(networkParameters)
            assertThat(netParamsResponse.sig.by).isEqualTo(networkMapCa.certificate)
            assertThatExceptionOfType(IOException::class.java)
                    .isThrownBy { it.doGet<SignedNetworkParameters>("network-parameters/${randomSHA256()}") }
                    .withMessageContaining("404")
        }
    }

    private fun NetworkManagementWebServer.doPost(path: String, payload: ByteArray) {
        val url = URL("http://$hostAndPort/network-map/$path")
        url.openHttpConnection().apply {
            doOutput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", MediaType.APPLICATION_OCTET_STREAM)
            outputStream.write(payload)
            checkOkResponse()
        }
    }

    private inline fun <reified T : Any> NetworkManagementWebServer.doGet(path: String): T {
        return URL("http://$hostAndPort/network-map/$path").openHttpConnection().responseAs()
    }
}
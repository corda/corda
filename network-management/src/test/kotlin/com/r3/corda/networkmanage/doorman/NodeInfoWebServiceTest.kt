package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.common.utils.withCert
import com.r3.corda.networkmanage.doorman.webservice.NodeInfoWebService
import net.corda.core.crypto.SecureHash.Companion.randomSHA256
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.openHttpConnection
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.createNodeInfoAndSigned
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URL
import java.nio.charset.Charset
import java.security.cert.X509Certificate
import javax.ws.rs.core.MediaType
import kotlin.test.assertEquals

class NodeInfoWebServiceTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private lateinit var rootCaCert: X509Certificate
    private lateinit var intermediateCa: CertificateAndKeyPair

    private val testNetworkMapConfig = NetworkMapConfig(10.seconds.toMillis(), 10.seconds.toMillis())

    @Before
    fun init() {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        this.intermediateCa = intermediateCa
    }

    @Test
    fun `submit nodeInfo`() {
        val networkMapStorage: NetworkMapStorage = mock {
            on { getCurrentNetworkParameters() }.thenReturn(testNetworkParameters(emptyList()))
        }
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"))

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val nodeInfoAndSignature = signedNodeInfo.serialize().bytes
            // Post node info and signature to doorman, this should pass without any exception.
            it.doPost("publish", nodeInfoAndSignature)
        }
    }

    @Test
    fun `submit old nodeInfo`() {
        val networkMapStorage: NetworkMapStorage = mock {
            on { getCurrentNetworkParameters() }.thenReturn(testNetworkParameters(emptyList(), minimumPlatformVersion = 2))
        }
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"), platformVersion = 1)

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val nodeInfoAndSignature = signedNodeInfo.serialize().bytes
            assertThatThrownBy { it.doPost("publish", nodeInfoAndSignature) }
                    .hasMessageStartingWith("Response Code 400: Minimum platform version is 2")
        }
    }

    @Test
    fun `submit nodeInfo when no network parameters`() {
        val networkMapStorage: NetworkMapStorage = mock {
            on { getCurrentNetworkParameters() }.thenReturn(null)
        }
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"), platformVersion = 1)

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val nodeInfoAndSignature = signedNodeInfo.serialize().bytes
            assertThatThrownBy { it.doPost("publish", nodeInfoAndSignature) }
                    .hasMessageStartingWith("Response Code 503: Network parameters have not been initialised")
        }
    }

    @Test
    fun `get network map`() {
        val networkMap = NetworkMap(listOf(randomSHA256(), randomSHA256()), randomSHA256())
        val serializedNetworkMap = networkMap.serialize()
        val signedNetworkMap = SignedNetworkMap(serializedNetworkMap, intermediateCa.keyPair.sign(serializedNetworkMap).withCert(intermediateCa.certificate))

        val networkMapStorage: NetworkMapStorage = mock {
            on { getCurrentNetworkMap() }.thenReturn(signedNetworkMap)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val signedNetworkMapResponse = it.doGet<SignedNetworkMap>("")
            verify(networkMapStorage, times(1)).getCurrentNetworkMap()
            assertEquals(signedNetworkMapResponse.verified(rootCaCert), networkMap)
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
        val signedNetParams = SignedData(serializedNetParams, intermediateCa.keyPair.sign(serializedNetParams))
        val netParamsHash = serializedNetParams.hash

        val networkMapStorage: NetworkMapStorage = mock {
            on { getSignedNetworkParameters(netParamsHash) }.thenReturn(signedNetParams)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NodeInfoWebService(mock(), networkMapStorage, testNetworkMapConfig)).use {
            it.start()
            val netParamsResponse = it.doGet<SignedData<NetworkParameters>>("network-parameter/$netParamsHash")
            verify(networkMapStorage, times(1)).getSignedNetworkParameters(netParamsHash)
            assertThat(netParamsResponse.verified()).isEqualTo(netParams)
            assertThat(netParamsResponse.sig.by).isEqualTo(intermediateCa.keyPair.public)

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
            if (responseCode != 200) {
                throw IOException("Response Code $responseCode: ${IOUtils.toString(errorStream, Charset.defaultCharset())}")
            }
            inputStream.close()
        }
    }

    private inline fun <reified T : Any> NetworkManagementWebServer.doGet(path: String): T {
        val url = URL("http://$hostAndPort/network-map/$path")
        return url.openHttpConnection().inputStream.use { it.readBytes().deserialize() }
    }
}
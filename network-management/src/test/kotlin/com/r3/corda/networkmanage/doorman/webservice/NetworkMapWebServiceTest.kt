/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman.webservice

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.common.persistence.CertificateSigningRequestStorage
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.persistence.NodeInfoStorage
import com.r3.corda.networkmanage.createNetworkMaps
import com.r3.corda.networkmanage.doorman.NetworkManagementWebServer
import com.r3.corda.networkmanage.doorman.NetworkMapConfig
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SecureHash.Companion.randomSHA256
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.post
import net.corda.core.internal.responseAs
import net.corda.core.internal.sign
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.first
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.mapOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkMapWebServiceTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private lateinit var rootCaCert: X509Certificate
    private lateinit var signingCertAndKeyPair: CertificateAndKeyPair

    private val testNetworkMapConfig = NetworkMapConfig(10.seconds.toMillis(), 10.seconds.toMillis())

    @Before
    fun init() {
        val (rootCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        signingCertAndKeyPair = createDevNetworkMapCa(rootCa)
    }

    @Test
    fun `submit nodeInfo`() {
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"))
        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkMaps() }.thenReturn(createNetworkMaps())
        }
        val csrStorage: CertificateSigningRequestStorage = mock {
            on { getValidCertificatePath(any()) }.thenReturn(signedNodeInfo.verified().legalIdentitiesAndCerts.first().certPath)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, csrStorage, testNetworkMapConfig)).use {
            it.start()
            // Post node info and signature to doorman, this should pass without any exception.
            it.doPost("publish", signedNodeInfo.serialize())
        }
    }

    @Test
    fun `submit nodeInfo with an unknown public key fails`() {
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"))
        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkMaps() }.thenReturn(createNetworkMaps())
        }
        val csrStorage: CertificateSigningRequestStorage = mock {
            on { getValidCertificatePath(any()) }.thenReturn(null)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, csrStorage, testNetworkMapConfig)).use {
            it.start()
            // Post node info and signature to doorman, this should pass without any exception.
            assertFailsWith<IOException>("Response Code 400") {
                it.doPost("publish", signedNodeInfo.serialize())
            }
        }
    }

    @Test
    fun `submit old nodeInfo`() {
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"), platformVersion = 1)
        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkMaps() }.thenReturn(createNetworkMaps(networkParameters = testNetworkParameters(minimumPlatformVersion = 2)))
        }
        val csrStorage: CertificateSigningRequestStorage = mock {
            on { getValidCertificatePath(any()) }.thenReturn(signedNodeInfo.verified().legalIdentitiesAndCerts.first().certPath)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, csrStorage, testNetworkMapConfig)).use {
            it.start()
            assertThatThrownBy { it.doPost("publish", signedNodeInfo.serialize()) }
                    .hasMessageStartingWith("Response Code 400: Minimum platform version is 2")
        }
    }

    @Test
    fun `submit nodeInfo when no network map`() {
        // Create node info.
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"), platformVersion = 1)
        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkMaps() }.thenReturn(null)
        }
        val csrStorage: CertificateSigningRequestStorage = mock {
            on { getValidCertificatePath(any()) }.thenReturn(signedNodeInfo.verified().legalIdentitiesAndCerts.first().certPath)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, csrStorage, testNetworkMapConfig)).use {
            it.start()
            assertThatThrownBy { it.doPost("publish", signedNodeInfo.serialize()) }
                    .hasMessageStartingWith("Response Code 503: Network parameters have not been initialised")
        }
    }

    @Test
    fun `get network map`() {
        val networkMaps = createNetworkMaps(
                signingCertAndKeyPair = signingCertAndKeyPair,
                nodeInfoHashes = listOf(randomSHA256(), randomSHA256()))

        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkMaps() }.thenReturn(networkMaps)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, mock(), testNetworkMapConfig)).use {
            it.start()
            val signedNetworkMapResponse = it.doGet<SignedNetworkMap>("")
            verify(networkMapStorage, times(1)).getNetworkMaps()
            assertEquals(signedNetworkMapResponse.verifiedNetworkMapCert(rootCaCert), networkMaps.publicNetworkMap!!.networkMap)
        }
    }

    @Test
    fun `network map response contains correct http header parameter`() {
        val timestamp = Instant.now()
        val networkMaps = createNetworkMaps(
                signingCertAndKeyPair = signingCertAndKeyPair,
                nodeInfoHashes = listOf(randomSHA256(), randomSHA256()),
                timestamp = timestamp)

        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkMaps() }.thenReturn(networkMaps)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, mock(), testNetworkMapConfig)).use {
            it.start()
            val headers = URL("http://${it.hostAndPort}/network-map/").openHttpConnection().headerFields
            assertEquals("max-age=${Duration.ofMillis(testNetworkMapConfig.cacheTimeout).seconds}", headers["Cache-Control"]?.first())
            assertEquals(DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT")).format(timestamp), headers["Last-Modified"]?.first())
        }
    }

    @Test
    fun `get private network map`() {
        val publicNodeInfoHashes = listOf(randomSHA256(), randomSHA256())
        val privateNodeInfoHashes = mapOf("PrivateNet1" to listOf(randomSHA256(), randomSHA256()), "PrivateNet2" to listOf(randomSHA256(), randomSHA256()))

        val networkMaps = createNetworkMaps(
                signingCertAndKeyPair = signingCertAndKeyPair,
                nodeInfoHashes = publicNodeInfoHashes,
                privateNodeInfoHashes = privateNodeInfoHashes
        )

        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkMaps() }.thenReturn(networkMaps)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, mock(), testNetworkMapConfig)).use {
            it.start()
            val signedNetworkMapResponse = it.doGet<SignedNetworkMap>("")
            verify(networkMapStorage, times(1)).getNetworkMaps()
            assertEquals(signedNetworkMapResponse.verifiedNetworkMapCert(rootCaCert), networkMaps.publicNetworkMap!!.networkMap)

            networkMaps.privateNetworkMap.forEach { (key, privateNetworkMapEntity) ->
                val response = it.doGet<SignedNetworkMap>(key)
                // Result cached.
                verify(networkMapStorage, times(1)).getNetworkMaps()
                assertEquals(response.verifiedNetworkMapCert(rootCaCert), privateNetworkMapEntity.networkMap)
            }
        }
    }

    @Test
    fun `get node info`() {
        // Mock node info storage
        val (nodeInfo, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name("Test", "London", "GB"))
        val nodeInfoHash = nodeInfo.serialize().hash
        val nodeInfoStorage: NodeInfoStorage = mock {
            on { getNodeInfo(nodeInfoHash) }.thenReturn(signedNodeInfo)
        }

        // Mock network map storage
        val networkMapStorage: NetworkMapStorage = mock {
            on { getNetworkMaps() }.thenReturn(createNetworkMaps(nodeInfoHashes = listOf(nodeInfoHash)))
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(nodeInfoStorage, networkMapStorage, mock(), testNetworkMapConfig)).use {
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
        val networkParameters = testNetworkParameters()
        val signedNetworkParameters = signingCertAndKeyPair.sign(networkParameters)
        val networkParametersHash = signedNetworkParameters.raw.hash

        val networkMapStorage: NetworkMapStorage = mock {
            on { getSignedNetworkParameters(networkParametersHash) }.thenReturn(signedNetworkParameters)
        }

        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(mock(), networkMapStorage, mock(), testNetworkMapConfig)).use {
            it.start()
            val netParamsResponse = it.doGet<SignedNetworkParameters>("network-parameters/$networkParametersHash")
            verify(networkMapStorage, times(1)).getSignedNetworkParameters(networkParametersHash)
            assertThat(netParamsResponse.verified()).isEqualTo(networkParameters)
            assertThat(netParamsResponse.sig.by).isEqualTo(signingCertAndKeyPair.certificate)
            assertThatExceptionOfType(IOException::class.java)
                    .isThrownBy { it.doGet<SignedNetworkParameters>("network-parameters/${randomSHA256()}") }
                    .withMessageContaining("404")
        }
    }

    @Test
    fun `ack network parameters update`() {
        val netParams = testNetworkParameters()
        val hash = netParams.serialize().hash
        val nodeInfoStorage: NodeInfoStorage = mock {
            on { ackNodeInfoParametersUpdate(any(), eq(hash)) }.then { Unit }
        }
        val networkMapStorage: NetworkMapStorage = mock {
            on { getSignedNetworkParameters(hash) }.thenReturn(signingCertAndKeyPair.sign(netParams))
        }
        NetworkManagementWebServer(NetworkHostAndPort("localhost", 0), NetworkMapWebService(nodeInfoStorage, networkMapStorage, mock(), testNetworkMapConfig)).use {
            it.start()
            val keyPair = Crypto.generateKeyPair()
            val signedHash = hash.serialize().sign { keyPair.sign(it) }
            it.doPost("ack-parameters", signedHash.serialize())
            verify(nodeInfoStorage).ackNodeInfoParametersUpdate(keyPair.public, hash)
            val randomSigned = SecureHash.randomSHA256().serialize().sign { keyPair.sign(it) }
            assertThatThrownBy { it.doPost("ack-parameters", randomSigned.serialize()) }
                    .hasMessageContaining("HTTP ERROR 500")
            val badSigned = SignedData(signedHash.raw, randomSigned.sig)
            assertThatThrownBy { it.doPost("ack-parameters", badSigned.serialize()) }
                    .hasMessageStartingWith("Response Code 403: Signature Verification failed!")
        }
    }

    private fun NetworkManagementWebServer.doPost(path: String, payload: OpaqueBytes) {
        URL("http://$hostAndPort/network-map/$path").post(payload)
    }

    private inline fun <reified T : Any> NetworkManagementWebServer.doGet(path: String): T {
        return URL("http://$hostAndPort/network-map/$path").openHttpConnection().responseAs()
    }
}

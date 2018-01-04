package com.r3.corda.networkmanage.common.signer

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.utils.withCert
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.crypto.sign
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkMapSignerTest : TestBase() {
    private lateinit var signer: Signer
    private lateinit var networkMapStorage: NetworkMapStorage
    private lateinit var networkMapSigner: NetworkMapSigner

    private lateinit var rootCaCert: X509Certificate
    private lateinit var intermediateCa: CertificateAndKeyPair

    @Before
    fun setUp() {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        this.intermediateCa = intermediateCa
        signer = mock()
        networkMapStorage = mock()
        networkMapSigner = NetworkMapSigner(networkMapStorage, signer)
    }

    @Test
    fun `signNetworkMap builds and signs network map`() {
        // given
        val signedNodeInfoHashes = listOf(SecureHash.randomSHA256(), SecureHash.randomSHA256())
        val networkParameters = testNetworkParameters(emptyList())
        val serializedNetworkMap = NetworkMap(signedNodeInfoHashes, SecureHash.randomSHA256()).serialize()
        whenever(networkMapStorage.getCurrentNetworkMap())
                .thenReturn(SignedNetworkMap(serializedNetworkMap, intermediateCa.keyPair.sign(serializedNetworkMap).withCert(intermediateCa.certificate)))
        whenever(networkMapStorage.getNodeInfoHashes(any())).thenReturn(signedNodeInfoHashes)
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(networkParameters)
        whenever(signer.sign(any())).then {
            intermediateCa.keyPair.sign(it.arguments[0] as ByteArray).withCert(intermediateCa.certificate)
        }

        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getNodeInfoHashes(any())
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<SignedNetworkMap>().apply {
            verify(networkMapStorage).saveNetworkMap(capture())
            val networkMap = firstValue.verified(rootCaCert)
            assertEquals(networkParameters.serialize().hash, networkMap.networkParameterHash)
            assertEquals(signedNodeInfoHashes.size, networkMap.nodeInfoHashes.size)
            assertTrue(networkMap.nodeInfoHashes.containsAll(signedNodeInfoHashes))
        }
    }

    @Test
    fun `signNetworkMap does NOT create a new network map if there are no changes`() {
        // given
        val networkParameters = testNetworkParameters(emptyList())
        val networkMapParametersHash = networkParameters.serialize().bytes.sha256()
        val networkMap = NetworkMap(emptyList(), networkMapParametersHash)
        val serializedNetworkMap = networkMap.serialize()
        val signedNetworkMap = SignedNetworkMap(serializedNetworkMap, intermediateCa.keyPair.sign(serializedNetworkMap).withCert(intermediateCa.certificate))
        whenever(networkMapStorage.getCurrentNetworkMap()).thenReturn(signedNetworkMap)
        whenever(networkMapStorage.getNodeInfoHashes(any())).thenReturn(emptyList())
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(networkParameters)

        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage is not called
        verify(networkMapStorage, never()).saveNetworkMap(any())
    }

    @Test
    fun `signNetworkMap creates a new network map if there is no current network map`() {
        // given
        val networkParameters = testNetworkParameters(emptyList())
        whenever(networkMapStorage.getCurrentNetworkMap()).thenReturn(null)
        whenever(networkMapStorage.getNodeInfoHashes(any())).thenReturn(emptyList())
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(networkParameters)
        whenever(signer.sign(any())).then {
            intermediateCa.keyPair.sign(it.arguments[0] as ByteArray).withCert(intermediateCa.certificate)
        }
        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getNodeInfoHashes(any())
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<SignedNetworkMap>().apply {
            verify(networkMapStorage).saveNetworkMap(capture())
            val networkMap = firstValue.verified(rootCaCert)
            assertEquals(networkParameters.serialize().hash, networkMap.networkParameterHash)
        }
    }
}
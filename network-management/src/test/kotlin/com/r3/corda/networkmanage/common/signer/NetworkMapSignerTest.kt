package com.r3.corda.networkmanage.common.signer

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import com.r3.corda.networkmanage.common.utils.SignedNetworkMap
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate
import kotlin.test.assertEquals

class NetworkMapSignerTest : TestBase() {
    private lateinit var signer: Signer
    private lateinit var networkMapStorage: NetworkMapStorage
    private lateinit var networkMapSigner: NetworkMapSigner

    private lateinit var rootCaCert: X509Certificate
    private lateinit var networkMapCa: CertificateAndKeyPair

    @Before
    fun setUp() {
        val (rootCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        networkMapCa = createDevNetworkMapCa(rootCa)
        signer = mock()
        networkMapStorage = mock()
        networkMapSigner = NetworkMapSigner(networkMapStorage, signer)
    }

    @Test
    fun `signNetworkMap builds and signs network map`() {
        // given
        val signedNodeInfoHashes = listOf(SecureHash.randomSHA256(), SecureHash.randomSHA256())
        val networkParameters = testNetworkParameters(emptyList())
        val networkMap = NetworkMap(signedNodeInfoHashes, SecureHash.randomSHA256())
        val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
        whenever(networkMapStorage.getCurrentNetworkMap()).thenReturn(signedNetworkMap)
        whenever(networkMapStorage.getNodeInfoHashes(any())).thenReturn(signedNodeInfoHashes)
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(networkParameters)
        whenever(signer.signBytes(any())).then {
            DigitalSignatureWithCert(networkMapCa.certificate, Crypto.doSign(networkMapCa.keyPair.private, it.arguments[0] as ByteArray))
        }

        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getNodeInfoHashes(any())
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<SignedNetworkMap>().apply {
            verify(networkMapStorage).saveNetworkMap(capture())
            val capturedNetworkMap = firstValue.verifiedNetworkMapCert(rootCaCert)
            assertEquals(networkParameters.serialize().hash, capturedNetworkMap.networkParameterHash)
            assertEquals(signedNodeInfoHashes.size, capturedNetworkMap.nodeInfoHashes.size)
            assertThat(capturedNetworkMap.nodeInfoHashes).containsAll(signedNodeInfoHashes)
        }
    }

    @Test
    fun `signNetworkMap does NOT create a new network map if there are no changes`() {
        // given
        val networkParameters = testNetworkParameters(emptyList())
        val networkMapParametersHash = networkParameters.serialize().bytes.sha256()
        val networkMap = NetworkMap(emptyList(), networkMapParametersHash)
        val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
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
        whenever(signer.signBytes(any())).then {
            DigitalSignatureWithCert(networkMapCa.certificate, Crypto.doSign(networkMapCa.keyPair.private, it.arguments[0] as ByteArray))
        }
        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getNodeInfoHashes(any())
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<SignedNetworkMap>().apply {
            verify(networkMapStorage).saveNetworkMap(capture())
            val networkMap = firstValue.verifiedNetworkMapCert(rootCaCert)
            assertEquals(networkParameters.serialize().hash, networkMap.networkParameterHash)
        }
    }
}
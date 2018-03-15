/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.common.signer

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
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
    fun `signNetworkMap builds and signs network map and network parameters`() {
        // given
        val signedNodeInfoHashes = listOf(SecureHash.randomSHA256(), SecureHash.randomSHA256())
        val currentParameters = testNetworkParameters(emptyList(), minimumPlatformVersion = 1)
        val latestNetworkParameters = testNetworkParameters(emptyList(), minimumPlatformVersion = 2)
        val networkMap = NetworkMap(signedNodeInfoHashes, currentParameters.serialize().hash, null)
        val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
        whenever(networkMapStorage.getCurrentNetworkMap()).thenReturn(signedNetworkMap)
        whenever(networkMapStorage.getActiveNodeInfoHashes()).thenReturn(signedNodeInfoHashes)
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(latestNetworkParameters)
        whenever(networkMapStorage.getNetworkParametersOfNetworkMap()).thenReturn(currentParameters.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate))
        whenever(signer.signBytes(any())).then {
            DigitalSignatureWithCert(networkMapCa.certificate, Crypto.doSign(networkMapCa.keyPair.private, it.arguments[0] as ByteArray))
        }
        whenever(signer.signObject(latestNetworkParameters)).then {
            val serialised = latestNetworkParameters.serialize()
            SignedNetworkParameters(serialised, signer.signBytes(serialised.bytes))
        }

        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getActiveNodeInfoHashes()
        verify(networkMapStorage).getLatestNetworkParameters()
        verify(networkMapStorage).getNetworkParametersOfNetworkMap()
        argumentCaptor<SignedNetworkMap>().apply {
            verify(networkMapStorage).saveNetworkMap(capture())
            val capturedNetworkMap = firstValue.verifiedNetworkMapCert(rootCaCert)
            assertEquals(latestNetworkParameters.serialize().hash, capturedNetworkMap.networkParameterHash)
            assertEquals(signedNodeInfoHashes.size, capturedNetworkMap.nodeInfoHashes.size)
            assertThat(capturedNetworkMap.nodeInfoHashes).containsAll(signedNodeInfoHashes)
        }
    }

    @Test
    fun `signNetworkMap does NOT create a new network map if there are no changes`() {
        // given
        val networkParameters = testNetworkParameters(emptyList())
        val networkMapParametersHash = networkParameters.serialize().bytes.sha256()
        val networkMap = NetworkMap(emptyList(), networkMapParametersHash, null)
        val signedNetworkMap = networkMap.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
        whenever(networkMapStorage.getCurrentNetworkMap()).thenReturn(signedNetworkMap)
        whenever(networkMapStorage.getActiveNodeInfoHashes()).thenReturn(emptyList())
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(networkParameters)
        whenever(networkMapStorage.getNetworkParametersOfNetworkMap()).thenReturn(networkParameters.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate))

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
        whenever(networkMapStorage.getActiveNodeInfoHashes()).thenReturn(emptyList())
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(networkParameters)
        whenever(signer.signBytes(any())).then {
            DigitalSignatureWithCert(networkMapCa.certificate, Crypto.doSign(networkMapCa.keyPair.private, it.arguments[0] as ByteArray))
        }
        whenever(signer.signObject(networkParameters)).then {
            val serialised = networkParameters.serialize()
            SignedNetworkParameters(serialised, signer.signBytes(serialised.bytes))
        }
        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getActiveNodeInfoHashes()
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<SignedNetworkMap>().apply {
            verify(networkMapStorage).saveNetworkMap(capture())
            val networkMap = firstValue.verifiedNetworkMapCert(rootCaCert)
            assertEquals(networkParameters.serialize().hash, networkMap.networkParameterHash)
        }
    }
}
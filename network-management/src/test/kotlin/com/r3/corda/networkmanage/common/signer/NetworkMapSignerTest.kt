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
import com.r3.corda.networkmanage.createNetworkMapEntity
import com.r3.corda.networkmanage.createNetworkParametersEntity
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMapAndSigned
import net.corda.nodeapi.internal.network.SignedNetworkParameters
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
    private lateinit var signingCertAndKeyPair: CertificateAndKeyPair

    @Before
    fun setUp() {
        val (rootCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        signingCertAndKeyPair = createDevNetworkMapCa(rootCa)
        signer = mock()
        networkMapStorage = mock()
        networkMapSigner = NetworkMapSigner(networkMapStorage, signer)
    }

    @Test
    fun `signNetworkMap builds and signs network map and network parameters`() {
        // given
        val nodeInfoHashes = listOf(SecureHash.randomSHA256(), SecureHash.randomSHA256())
        val activeNetParams = testNetworkParameters(minimumPlatformVersion = 1)
        val latestNetParams = testNetworkParameters(minimumPlatformVersion = 2)
        val activeNetParamsEntity = createNetworkParametersEntity(signingCertAndKeyPair, activeNetParams)
        val latestNetParamsEntity = createNetworkParametersEntity(signingCertAndKeyPair, latestNetParams)
        val netMapEntity = createNetworkMapEntity(signingCertAndKeyPair, activeNetParamsEntity, nodeInfoHashes)
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(latestNetParamsEntity)
        whenever(networkMapStorage.getActiveNodeInfoHashes()).thenReturn(nodeInfoHashes)
        whenever(networkMapStorage.getActiveNetworkMap()).thenReturn(netMapEntity)
        whenever(signer.signBytes(any())).then {
            DigitalSignatureWithCert(signingCertAndKeyPair.certificate, Crypto.doSign(signingCertAndKeyPair.keyPair.private, it.arguments[0] as ByteArray))
        }
        whenever(signer.signObject(latestNetParams)).then {
            val serialised = latestNetParams.serialize()
            SignedNetworkParameters(serialised, signer.signBytes(serialised.bytes))
        }

        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getActiveNodeInfoHashes()
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<NetworkMapAndSigned>().apply {
            verify(networkMapStorage).saveNewActiveNetworkMap(capture())
            val capturedNetworkMap = firstValue.networkMap
            assertEquals(latestNetParams.serialize().hash, capturedNetworkMap.networkParameterHash)
            assertThat(capturedNetworkMap.nodeInfoHashes).isEqualTo(nodeInfoHashes)
        }
    }

    @Test
    fun `signNetworkMap does NOT create a new network map if there are no changes`() {
        // given
        val netParamsEntity = createNetworkParametersEntity(signingCertAndKeyPair)
        val netMapEntity = createNetworkMapEntity(signingCertAndKeyPair, netParamsEntity, emptyList())
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(netParamsEntity)
        whenever(networkMapStorage.getActiveNodeInfoHashes()).thenReturn(emptyList())
        whenever(networkMapStorage.getActiveNetworkMap()).thenReturn(netMapEntity)

        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage is not called
        verify(networkMapStorage, never()).saveNewActiveNetworkMap(any())
    }

    @Test
    fun `signNetworkMap creates a new network map if there is no current network map`() {
        // given
        val netParams = testNetworkParameters()
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(createNetworkParametersEntity(signingCertAndKeyPair, netParams))
        whenever(networkMapStorage.getActiveNodeInfoHashes()).thenReturn(emptyList())
        whenever(networkMapStorage.getActiveNetworkMap()).thenReturn(null)
        whenever(signer.signBytes(any())).then {
            DigitalSignatureWithCert(signingCertAndKeyPair.certificate, Crypto.doSign(signingCertAndKeyPair.keyPair.private, it.arguments[0] as ByteArray))
        }
        whenever(signer.signObject(netParams)).then {
            val serialised = netParams.serialize()
            SignedNetworkParameters(serialised, signer.signBytes(serialised.bytes))
        }
        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getActiveNodeInfoHashes()
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<NetworkMapAndSigned>().apply {
            verify(networkMapStorage).saveNewActiveNetworkMap(capture())
            assertEquals(netParams.serialize().hash, firstValue.networkMap.networkParameterHash)
        }
    }
}

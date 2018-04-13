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
import com.r3.corda.networkmanage.common.persistence.NetworkMaps
import com.r3.corda.networkmanage.common.persistence.NodeInfoHashes
import com.r3.corda.networkmanage.common.persistence.entity.ParametersUpdateEntity
import com.r3.corda.networkmanage.common.persistence.entity.UpdateStatus
import com.r3.corda.networkmanage.createNetworkMaps
import com.r3.corda.networkmanage.createNetworkParametersEntity
import com.r3.corda.networkmanage.createNetworkParametersEntityUnsigned
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializedBytes
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
import java.time.Instant
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
        whenever(signer.signBytes(any())).then {
            DigitalSignatureWithCert(signingCertAndKeyPair.certificate, Crypto.doSign(signingCertAndKeyPair.keyPair.private, it.arguments[0] as ByteArray))
        }
        whenever(signer.signObject(any<NetworkParameters>())).then {
            val serialised: SerializedBytes<NetworkParameters> = uncheckedCast(it.arguments[0].serialize())
            SignedNetworkParameters(serialised, signer.signBytes(serialised.bytes))
        }
    }

    @Test
    fun `signNetworkMap builds and signs network map and network parameters`() {
        // given
        val nodeInfoHashes = NodeInfoHashes(listOf(SecureHash.randomSHA256(), SecureHash.randomSHA256()), emptyMap())
        val latestNetParams = testNetworkParameters(epoch = 3)
        val latestNetParamsEntity = createNetworkParametersEntityUnsigned(latestNetParams)
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(latestNetParamsEntity)
        whenever(networkMapStorage.getNodeInfoHashes()).thenReturn(nodeInfoHashes)
        whenever(networkMapStorage.getNetworkMaps()).thenReturn(NetworkMaps(null, emptyMap()))
        whenever(networkMapStorage.getCurrentParametersUpdate()).thenReturn(null)

        // when
        networkMapSigner.signNetworkMaps()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getNodeInfoHashes()
        verify(networkMapStorage).getNetworkMaps()
        verify(networkMapStorage).getCurrentParametersUpdate()
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<NetworkMapAndSigned>().apply {
            verify(networkMapStorage).saveNewNetworkMap(anyOrNull(), capture())
            val capturedNetworkMap = firstValue.networkMap
            // Parameters in network map got swapped for latest ones.
            assertEquals(latestNetParams.serialize().hash, capturedNetworkMap.networkParameterHash)
            assertThat(capturedNetworkMap.nodeInfoHashes).isEqualTo(nodeInfoHashes.publicNodeInfoHashes)
        }
        val paramsCaptor = argumentCaptor<NetworkParameters>()
        val signatureCaptor = argumentCaptor<DigitalSignatureWithCert>()
        verify(networkMapStorage).saveNetworkParameters(paramsCaptor.capture(), signatureCaptor.capture())
        assertEquals(paramsCaptor.firstValue, latestNetParams)
        assertThat(signatureCaptor.firstValue.verify(latestNetParams.serialize()))
    }

    @Test
    fun `signNetworkMap does NOT create a new network map if there are no changes`() {
        // given
        val netParamsEntity = createNetworkParametersEntity(signingCertAndKeyPair)
        val netMapEntity = createNetworkMaps(signingCertAndKeyPair, netParamsEntity, emptyList())
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(netParamsEntity)
        whenever(networkMapStorage.getNodeInfoHashes()).thenReturn(NodeInfoHashes(emptyList(), emptyMap()))
        whenever(networkMapStorage.getNetworkMaps()).thenReturn(netMapEntity)

        // when
        networkMapSigner.signNetworkMaps()

        // then
        // Verify networkMapStorage is not called
        verify(networkMapStorage, never()).saveNewNetworkMap(any(), any())
        verify(networkMapStorage, never()).saveNetworkParameters(any(), any())
    }

    @Test
    fun `signNetworkMap creates a new network map if there is no current network map`() {
        // given
        val netParams = testNetworkParameters()
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(createNetworkParametersEntityUnsigned(netParams))
        whenever(networkMapStorage.getNodeInfoHashes()).thenReturn(NodeInfoHashes(emptyList(), emptyMap()))
        whenever(networkMapStorage.getNetworkMaps()).thenReturn(NetworkMaps(null, emptyMap()))
        whenever(networkMapStorage.getCurrentParametersUpdate()).thenReturn(null)

        // when
        networkMapSigner.signNetworkMaps()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getNodeInfoHashes()
        verify(networkMapStorage).getNetworkMaps()
        verify(networkMapStorage).getLatestNetworkParameters()
        verify(networkMapStorage).getCurrentParametersUpdate()
        argumentCaptor<NetworkMapAndSigned>().apply {
            verify(networkMapStorage).saveNewNetworkMap(anyOrNull(), capture())
            assertEquals(netParams.serialize().hash, firstValue.networkMap.networkParameterHash)
        }
        val paramsCaptor = argumentCaptor<NetworkParameters>()
        val signatureCaptor = argumentCaptor<DigitalSignatureWithCert>()
        verify(networkMapStorage).saveNetworkParameters(paramsCaptor.capture(), signatureCaptor.capture())
        assertEquals(paramsCaptor.firstValue, netParams)
        assertThat(signatureCaptor.firstValue.verify(netParams.serialize()))
    }

    @Test
    fun `signNetworkMap signs new network parameters`() {
        // given
        val currentNetworkParameters = createNetworkParametersEntity(signingCertAndKeyPair)
        val updateNetworkParameters = createNetworkParametersEntityUnsigned(testNetworkParameters(epoch = 2))
        val parametersUpdate = ParametersUpdateEntity(0, updateNetworkParameters, "Update time", Instant.ofEpochMilli(random63BitValue()))
        val netMapEntity = createNetworkMaps(signingCertAndKeyPair, currentNetworkParameters)
        whenever(networkMapStorage.getNetworkMaps()).thenReturn(netMapEntity)
        whenever(networkMapStorage.getCurrentParametersUpdate()).thenReturn(parametersUpdate)
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(updateNetworkParameters)
        whenever(networkMapStorage.getNodeInfoHashes()).thenReturn(NodeInfoHashes(emptyList(), emptyMap()))

        // when
        networkMapSigner.signNetworkMaps()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getNetworkMaps()
        verify(networkMapStorage).getNodeInfoHashes()
        verify(networkMapStorage).getLatestNetworkParameters()
        verify(networkMapStorage).getCurrentParametersUpdate()

        val paramsCaptor = argumentCaptor<NetworkParameters>()
        val signatureCaptor = argumentCaptor<DigitalSignatureWithCert>()
        verify(networkMapStorage, times(1)).saveNetworkParameters(paramsCaptor.capture(), signatureCaptor.capture())
        assertEquals(paramsCaptor.firstValue, updateNetworkParameters.networkParameters)
        signatureCaptor.firstValue.verify(updateNetworkParameters.networkParameters.serialize())
    }

    @Test
    fun `signNetworkMap fails if there is parameter update without relevant parameters stored`() {
        val updateNetworkParameters = createNetworkParametersEntityUnsigned(testNetworkParameters(epoch = 2))
        val parametersUpdate = ParametersUpdateEntity(0, updateNetworkParameters, "Update time", Instant.ofEpochMilli(random63BitValue()))
        whenever(networkMapStorage.getNetworkMaps()).thenReturn(NetworkMaps(null, emptyMap()))
        whenever(networkMapStorage.getCurrentParametersUpdate()).thenReturn(parametersUpdate)
        whenever(networkMapStorage.getNodeInfoHashes()).thenReturn(NodeInfoHashes(emptyList(), emptyMap()))
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(createNetworkParametersEntity())

        verify(networkMapStorage, never()).saveNetworkParameters(any(), any())
        verify(networkMapStorage, never()).saveNewNetworkMap(any(), any())
    }

    @Test
    fun `setting flag day on parameters update changes parameters inside network map`() {
        val activeNetworkParameters = createNetworkParametersEntity(signingCertAndKeyPair, testNetworkParameters(epoch = 1))
        val updateNetworkParameters = createNetworkParametersEntity(signingCertAndKeyPair, testNetworkParameters(epoch = 2))
        val parametersUpdate = ParametersUpdateEntity(0, updateNetworkParameters, "Update time", Instant.ofEpochMilli(random63BitValue()))
        val activeNetworkMaps = createNetworkMaps(signingCertAndKeyPair, activeNetworkParameters, parametersUpdate = parametersUpdate.toParametersUpdate())

        whenever(networkMapStorage.getNetworkMaps()).thenReturn(activeNetworkMaps)
        whenever(networkMapStorage.getNodeInfoHashes()).thenReturn(NodeInfoHashes(emptyList(), emptyMap()))
        whenever(networkMapStorage.getCurrentParametersUpdate()).thenReturn(parametersUpdate.copy(status = UpdateStatus.FLAG_DAY))
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(updateNetworkParameters)

        // when
        networkMapSigner.signNetworkMaps()

        //then
        argumentCaptor<NetworkMapAndSigned>().apply {
            verify(networkMapStorage).saveNewNetworkMap(anyOrNull(), capture())
            val netMap = firstValue.networkMap
            assertEquals(SecureHash.parse(updateNetworkParameters.hash), netMap.networkParameterHash)
            assertEquals(emptyList(), netMap.nodeInfoHashes)
            assertEquals(null, netMap.parametersUpdate)
        }
    }

    @Test
    fun `cancel update test`() {
        val activeNetworkParameters = createNetworkParametersEntity(signingCertAndKeyPair, testNetworkParameters(epoch = 1))
        val updateNetworkParameters = createNetworkParametersEntityUnsigned(testNetworkParameters(epoch = 2))
        val parametersUpdate = ParametersUpdateEntity(0, updateNetworkParameters, "Update time", Instant.ofEpochMilli(random63BitValue()))
        val activeNetworkMaps = createNetworkMaps(signingCertAndKeyPair, activeNetworkParameters, parametersUpdate = parametersUpdate.toParametersUpdate())

        whenever(networkMapStorage.getNetworkMaps()).thenReturn(activeNetworkMaps)
        whenever(networkMapStorage.getNodeInfoHashes()).thenReturn(NodeInfoHashes(emptyList(), emptyMap()))
        whenever(networkMapStorage.getCurrentParametersUpdate()).thenReturn(null)
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(createNetworkParametersEntity())

        // when
        networkMapSigner.signNetworkMaps()

        //then
        argumentCaptor<NetworkMapAndSigned>().apply {
            verify(networkMapStorage).saveNewNetworkMap(anyOrNull(), capture())
            val netMap = firstValue.networkMap
            assertEquals(SecureHash.parse(activeNetworkParameters.hash), netMap.networkParameterHash)
            assertEquals(emptyList(), netMap.nodeInfoHashes)
            assertEquals(null, netMap.parametersUpdate)
        }
    }
}

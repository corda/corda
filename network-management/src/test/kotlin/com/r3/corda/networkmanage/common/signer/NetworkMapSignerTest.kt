package com.r3.corda.networkmanage.common.signer

import com.nhaarman.mockito_kotlin.*
import com.r3.corda.networkmanage.TestBase
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.serialization.serialize
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkMapSignerTest : TestBase() {
    private lateinit var signer: Signer
    private lateinit var networkMapStorage: NetworkMapStorage
    private lateinit var networkMapSigner: NetworkMapSigner

    @Before
    fun setUp() {
        signer = mock()
        networkMapStorage = mock()
        networkMapSigner = NetworkMapSigner(networkMapStorage, signer)
    }

    @Test
    fun `signNetworkMap builds and signs network map`() {
        // given
        val signedNodeInfoHashes = listOf(SecureHash.randomSHA256(), SecureHash.randomSHA256())
        val detachedNodeInfoHashes = listOf(SecureHash.randomSHA256())
        val networkMapParameters = createNetworkParameters()
        whenever(networkMapStorage.getCurrentNetworkMap())
                .thenReturn(SignedNetworkMap(NetworkMap(signedNodeInfoHashes.map { it.toString() }, "Dummy"), mock()))
        whenever(networkMapStorage.getCurrentNetworkMapNodeInfoHashes(any())).thenReturn(signedNodeInfoHashes)
        whenever(networkMapStorage.getDetachedSignedAndValidNodeInfoHashes()).thenReturn(detachedNodeInfoHashes)
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(networkMapParameters)
        whenever(signer.sign(any())).thenReturn(mock())

        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage calls
        verify(networkMapStorage).getCurrentNetworkMapNodeInfoHashes(any())
        verify(networkMapStorage).getDetachedSignedAndValidNodeInfoHashes()
        verify(networkMapStorage).getLatestNetworkParameters()
        argumentCaptor<SignedNetworkMap>().apply {
            verify(networkMapStorage).saveNetworkMap(capture())
            val networkMap = firstValue.networkMap
            assertEquals(networkMapParameters.serialize().hash.toString(), networkMap.parametersHash)
            assertEquals(signedNodeInfoHashes.size + detachedNodeInfoHashes.size, networkMap.nodeInfoHashes.size)
            assertTrue(networkMap.nodeInfoHashes.containsAll(signedNodeInfoHashes.map { it.toString() }))
            assertTrue(networkMap.nodeInfoHashes.containsAll(detachedNodeInfoHashes.map { it.toString() }))
        }
    }

    @Test
    fun `signNetworkMap does NOT create a new network map if there are no changes`() {
        // given
        val networkMapParameters = createNetworkParameters()
        val networkMapParametersHash = networkMapParameters.serialize().bytes.sha256()
        val networkMap = NetworkMap(emptyList(), networkMapParametersHash.toString())
        val signedNetworkMap = SignedNetworkMap(networkMap, mock())
        whenever(networkMapStorage.getCurrentNetworkMap()).thenReturn(signedNetworkMap)
        whenever(networkMapStorage.getCurrentNetworkMapNodeInfoHashes(any())).thenReturn(emptyList())
        whenever(networkMapStorage.getDetachedSignedAndValidNodeInfoHashes()).thenReturn(emptyList())
        whenever(networkMapStorage.getLatestNetworkParameters()).thenReturn(networkMapParameters)

        // when
        networkMapSigner.signNetworkMap()

        // then
        // Verify networkMapStorage is not called
        verify(networkMapStorage, never()).saveNetworkMap(any())
    }
}
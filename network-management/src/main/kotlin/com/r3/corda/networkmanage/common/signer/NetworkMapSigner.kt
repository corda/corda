package com.r3.corda.networkmanage.common.signer

import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkParameters

class NetworkMapSigner(private val networkMapStorage: NetworkMapStorage, private val signer: Signer) {
    /**
     * Signs the network map and latest network parameters if they haven't been signed yet.
     */
    fun signNetworkMap() {
        // TODO There is no network parameters update process in place yet. We assume that latest parameters are to be used
        // in current network map.
        val latestNetworkParameters = networkMapStorage.getLatestUnsignedNetworkParameters()
        val currentNetworkParameters = networkMapStorage.getCurrentSignedNetworkParameters()
        if (currentNetworkParameters?.verified() != latestNetworkParameters)
            signNetworkParameters(latestNetworkParameters)
        val currentSignedNetworkMap = networkMapStorage.getCurrentNetworkMap()
        val nodeInfoHashes = networkMapStorage.getNodeInfoHashes(CertificateStatus.VALID)
        val serialisedNetworkMap = NetworkMap(nodeInfoHashes, latestNetworkParameters.serialize().hash).serialize()
        if (serialisedNetworkMap != currentSignedNetworkMap?.raw) {
            val newSignedNetworkMap = SignedDataWithCert(serialisedNetworkMap, signer.signBytes(serialisedNetworkMap.bytes))
            networkMapStorage.saveNetworkMap(newSignedNetworkMap)
        }
    }

    /**
     * Signs latest inserted network parameters.
     */
    fun signNetworkParameters(networkParameters: NetworkParameters) {
        val digitalSignature = signer.signObject(networkParameters).sig
        networkMapStorage.saveNetworkParameters(networkParameters, digitalSignature)
    }
}
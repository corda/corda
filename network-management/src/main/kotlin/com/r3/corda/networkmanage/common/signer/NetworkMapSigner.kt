package com.r3.corda.networkmanage.common.signer

import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.network.NetworkMap

class NetworkMapSigner(private val networkMapStorage: NetworkMapStorage, private val signer: Signer) {
    /**
     * Signs the network map.
     */
    fun signNetworkMap() {
        val currentSignedNetworkMap = networkMapStorage.getCurrentNetworkMap()
        val nodeInfoHashes = networkMapStorage.getNodeInfoHashes(CertificateStatus.VALID)
        val networkParameters = networkMapStorage.getLatestNetworkParameters()
        val serialisedNetworkMap = NetworkMap(nodeInfoHashes, networkParameters.serialize().hash).serialize()
        if (serialisedNetworkMap != currentSignedNetworkMap?.raw) {
            val newSignedNetworkMap = SignedDataWithCert(serialisedNetworkMap, signer.signBytes(serialisedNetworkMap.bytes))
            networkMapStorage.saveNetworkMap(newSignedNetworkMap)
        }
    }
}
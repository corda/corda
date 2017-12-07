package com.r3.corda.networkmanage.common.signer

import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.NetworkMap
import net.corda.nodeapi.internal.SignedNetworkMap

class NetworkMapSigner(private val networkMapStorage: NetworkMapStorage, private val signer: Signer) {
    /**
     * Signs the network map.
     */
    fun signNetworkMap() {
        val currentSignedNetworkMap = networkMapStorage.getCurrentNetworkMap()
        val nodeInfoHashes = networkMapStorage.getNodeInfoHashes(CertificateStatus.VALID)
        val networkParameters = networkMapStorage.getLatestNetworkParameters()
        val networkMap = NetworkMap(nodeInfoHashes, networkParameters.serialize().hash)
        if (networkMap != currentSignedNetworkMap?.verified()) {
            val digitalSignature = signer.sign(networkMap.serialize().bytes)
            val signedHashedNetworkMap = SignedNetworkMap(networkMap.serialize(), digitalSignature)
            networkMapStorage.saveNetworkMap(signedHashedNetworkMap)
        }
    }
}
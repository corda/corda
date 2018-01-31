package com.r3.corda.networkmanage.common.signer

import com.r3.corda.networkmanage.common.persistence.CertificateStatus
import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkParameters

class NetworkMapSigner(private val networkMapStorage: NetworkMapStorage, private val signer: Signer) {
    private companion object {
        val logger = contextLogger()
    }

    /**
     * Signs the network map and latest network parameters if they haven't been signed yet.
     */
    fun signNetworkMap() {
        // TODO There is no network parameters update process in place yet. We assume that latest parameters are to be used
        // in current network map.
        val latestNetworkParameters = networkMapStorage.getLatestNetworkParameters()
        if (latestNetworkParameters == null) {
            logger.info("No network parameters present")
            return
        }
        val currentNetworkParameters = networkMapStorage.getNetworkParametersOfNetworkMap()
        if (currentNetworkParameters?.verified() != latestNetworkParameters) {
            persistSignedNetworkParameters(latestNetworkParameters)
        }
        val currentSignedNetworkMap = networkMapStorage.getCurrentNetworkMap()
        val nodeInfoHashes = networkMapStorage.getNodeInfoHashes(CertificateStatus.VALID)
        val serialisedNetworkMap = NetworkMap(nodeInfoHashes, latestNetworkParameters.serialize().hash).serialize()
        if (serialisedNetworkMap != currentSignedNetworkMap?.raw) {
            val newSignedNetworkMap = SignedDataWithCert(serialisedNetworkMap, signer.signBytes(serialisedNetworkMap.bytes))
            networkMapStorage.saveNetworkMap(newSignedNetworkMap)
        }
    }

    fun persistSignedNetworkParameters(networkParameters: NetworkParameters) {
        logger.info("Signing and persisting network parameters: $networkParameters")
        val digitalSignature = signer.signObject(networkParameters).sig
        networkMapStorage.saveNetworkParameters(networkParameters, digitalSignature)
    }
}
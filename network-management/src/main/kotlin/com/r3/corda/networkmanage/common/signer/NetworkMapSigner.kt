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
            logger.debug("No network parameters present")
            return
        }
        logger.debug("Fetching current network map parameters...")
        val currentNetworkParameters = networkMapStorage.getNetworkParametersOfNetworkMap()
        logger.debug("Retrieved network map parameters: $currentNetworkParameters")
        if (currentNetworkParameters?.verified() != latestNetworkParameters) {
            persistSignedNetworkParameters(latestNetworkParameters)
        } else {
            logger.debug("Network map parameters up-to-date. Skipping signing.")
        }
        logger.debug("Fetching current network map...")
        val currentSignedNetworkMap = networkMapStorage.getCurrentNetworkMap()
        logger.debug("Fetching node info hashes with VALID certificates...")
        val nodeInfoHashes = networkMapStorage.getNodeInfoHashes(CertificateStatus.VALID)
        logger.debug("Retrieved node info hashes: $nodeInfoHashes")
        val newNetworkMap = NetworkMap(nodeInfoHashes, latestNetworkParameters.serialize().hash)
        val serialisedNetworkMap = newNetworkMap.serialize()
        if (serialisedNetworkMap != currentSignedNetworkMap?.raw) {
            logger.info("Signing a new network map: $newNetworkMap")
            logger.debug("Creating a new signed network map: ${serialisedNetworkMap.hash}")
            val newSignedNetworkMap = SignedDataWithCert(serialisedNetworkMap, signer.signBytes(serialisedNetworkMap.bytes))
            networkMapStorage.saveNetworkMap(newSignedNetworkMap)
            logger.debug("Signed network map saved")
        } else {
            logger.debug("Current network map is up-to-date. Skipping signing.")
        }
    }

    fun persistSignedNetworkParameters(networkParameters: NetworkParameters) {
        logger.info("Signing and persisting network parameters: $networkParameters")
        val digitalSignature = signer.signObject(networkParameters).sig
        networkMapStorage.saveNetworkParameters(networkParameters, digitalSignature)
        logger.info("Signed network map parameters saved.")
    }
}
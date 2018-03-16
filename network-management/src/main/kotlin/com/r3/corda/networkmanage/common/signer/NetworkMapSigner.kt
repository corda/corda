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

import com.r3.corda.networkmanage.common.persistence.NetworkMapStorage
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkMapAndSigned

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
        val latestNetParamsEntity = networkMapStorage.getLatestNetworkParameters()
        if (latestNetParamsEntity == null) {
            logger.warn("No network parameters present")
            return
        }

        val latestNetParams = latestNetParamsEntity.toNetworkParameters()
        logger.debug { "Latest network parameters: $latestNetParams" }

        if (!latestNetParamsEntity.isSigned) {
            signAndPersistNetworkParameters(latestNetParams)
        } else {
            logger.debug("Network parameters are already signed")
        }

        val nodeInfoHashes = networkMapStorage.getActiveNodeInfoHashes()
        logger.debug { "Active node-info hashes:\n${nodeInfoHashes.joinToString("\n")}" }

        val currentNetworkMap = networkMapStorage.getActiveNetworkMap()?.toNetworkMap()
        if (currentNetworkMap != null) {
            logger.debug { "Current network map: $currentNetworkMap" }
        } else {
            logger.info("There is currently no network map")
        }

        val newNetworkMap = NetworkMap(nodeInfoHashes, SecureHash.parse(latestNetParamsEntity.parametersHash), null)
        logger.debug { "Potential new network map: $newNetworkMap" }

        if (currentNetworkMap != newNetworkMap) {
            val netNetworkMapAndSigned = NetworkMapAndSigned(newNetworkMap) { signer.signBytes(it.bytes) }
            networkMapStorage.saveNewActiveNetworkMap(netNetworkMapAndSigned)
            logger.info("Signed new network map: $newNetworkMap")
        } else {
            logger.debug("Current network map is up-to-date")
        }
    }

    fun signAndPersistNetworkParameters(networkParameters: NetworkParameters) {
        networkMapStorage.saveNetworkParameters(networkParameters, signer.signObject(networkParameters).sig)
        logger.info("Signed network parameters: $networkParameters")
    }
}

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
import com.r3.corda.networkmanage.common.persistence.entity.UpdateStatus.*
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
        val latestNetworkParameters = networkMapStorage.getLatestNetworkParameters()
        if (latestNetworkParameters == null) {
            logger.debug("No network parameters present")
            return
        }
        logger.debug { "Retrieved latest network parameters: ${latestNetworkParameters.networkParameters}" }

        val parametersUpdate = networkMapStorage.getCurrentParametersUpdate()
        logger.debug { "Retrieved parameters update: $parametersUpdate" }
        check(parametersUpdate == null || parametersUpdate.networkParameters.hash == latestNetworkParameters.hash) {
            "The latest network parameters are not the scheduled updated ones"
        }

        val activeNetworkMap = networkMapStorage.getActiveNetworkMap()
        if (activeNetworkMap == null) {
            logger.info("There is currently no network map")
        } else {
            logger.debug { "Current network map: ${activeNetworkMap.networkMap}" }
        }

        val activeNetworkParameters = activeNetworkMap?.networkParameters
        logger.debug { "Current network map parameters: ${activeNetworkParameters?.networkParameters}" }

        // We persist signed parameters only if they were not persisted before (they are not in currentSignedNetworkMap as
        // normal parameters or as an update)
        if (!latestNetworkParameters.isSigned) {
            signAndPersistNetworkParameters(latestNetworkParameters.networkParameters)
        } else {
            logger.debug { "No need to sign any network parameters as they're up-to-date" }
        }

        val parametersToNetworkMap = if (parametersUpdate?.status == FLAG_DAY || activeNetworkParameters == null) {
            parametersUpdate?.let { networkMapStorage.switchFlagDay(it) }
            latestNetworkParameters
        } else {
            activeNetworkParameters
        }

        val nodeInfoHashes = networkMapStorage.getActiveNodeInfoHashes()
        logger.debug { "Retrieved node info hashes:\n${nodeInfoHashes.joinToString("\n")}" }

        val newNetworkMap = NetworkMap(
                nodeInfoHashes,
                SecureHash.parse(parametersToNetworkMap.hash),
                parametersUpdate?.let { if (it.status == NEW) it.toParametersUpdate() else null })
        logger.debug { "Potential new network map: $newNetworkMap" }

        if (activeNetworkMap?.networkMap != newNetworkMap) {
            val newNetworkMapAndSigned = NetworkMapAndSigned(newNetworkMap) { signer.signBytes(it.bytes) }
            networkMapStorage.saveNewActiveNetworkMap(newNetworkMapAndSigned)
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

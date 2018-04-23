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
import com.r3.corda.networkmanage.common.persistence.entity.NetworkMapEntity
import com.r3.corda.networkmanage.common.persistence.entity.UpdateStatus.FLAG_DAY
import com.r3.corda.networkmanage.common.persistence.entity.UpdateStatus.NEW
import com.r3.corda.networkmanage.common.utils.join
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkMapAndSigned
import net.corda.nodeapi.internal.network.ParametersUpdate

class NetworkMapSigner(private val networkMapStorage: NetworkMapStorage, private val signer: Signer) {
    private companion object {
        val logger = contextLogger()
    }

    /**
     * Signs the network map and latest network parameters if they haven't been signed yet.
     */
    fun signNetworkMaps() {
        val (publicNetworkMap, privateNetworkMaps) = networkMapStorage.getNetworkMaps()
        val (publicNodeInfoHashes, privateNodeInfoHashes) = networkMapStorage.getNodeInfoHashes()

        val (networkParameterHash, parametersUpdate) = maybeUpdateNetworkParameters(publicNetworkMap?.networkMap?.networkParameterHash)
        logger.debug { "Current network parameters: $networkParameterHash" }

        // Process public network map.
        maybeSignNetworkMap(publicNetworkMap, publicNodeInfoHashes, parametersUpdate, networkParameterHash)

        // Process each private network map.
        privateNetworkMaps.join(privateNodeInfoHashes).forEach { networkId, (currentNetworkMap, nodeInfoHashes) ->
            maybeSignNetworkMap(currentNetworkMap, nodeInfoHashes, parametersUpdate, networkParameterHash, networkId)
        }
    }

    private fun maybeSignNetworkMap(currentNetworkMap: NetworkMapEntity?, nodeInfoHashes: List<SecureHash>?, parametersUpdate: ParametersUpdate?, networkParameterHash: SecureHash, networkId: String? = null) {
        val printableNetworkId = networkId ?: "Public Network"
        if (currentNetworkMap == null) {
            logger.info("There is currently no network map for network '$printableNetworkId'")
        } else {
            logger.debug { "Current network map for network '$printableNetworkId': ${currentNetworkMap.networkMap}" }
        }

        logger.debug { "Retrieved node info hashes for network '$printableNetworkId' :\n${nodeInfoHashes?.joinToString("\n")}" }

        val newNetworkMap = NetworkMap(nodeInfoHashes ?: emptyList(), networkParameterHash, parametersUpdate)
        logger.debug { "Potential new network map for network '$printableNetworkId': $newNetworkMap" }

        if (currentNetworkMap?.networkMap != newNetworkMap) {
            val newNetworkMapAndSigned = NetworkMapAndSigned(newNetworkMap) { signer.signBytes(it.bytes) }
            networkMapStorage.saveNewNetworkMap(networkId, newNetworkMapAndSigned)
            logger.info("Signed new network map for network '$printableNetworkId' : $newNetworkMap")
        } else {
            logger.debug("Current network map for network '$printableNetworkId' is up-to-date")
        }
    }

    private fun maybeUpdateNetworkParameters(currentNetworkParametersHash: SecureHash?): Pair<SecureHash, ParametersUpdate?> {
        val latestNetworkParameters = requireNotNull(networkMapStorage.getLatestNetworkParameters()) { "No network parameters present" }
        logger.debug { "Retrieved latest network parameters: ${latestNetworkParameters.networkParameters}" }

        val parametersUpdate = networkMapStorage.getCurrentParametersUpdate()
        logger.debug { "Retrieved parameters update: $parametersUpdate" }
        check(parametersUpdate == null || parametersUpdate.networkParameters.hash == latestNetworkParameters.hash) {
            "The latest network parameters are not the scheduled updated ones"
        }

        // We persist signed parameters only if they were not persisted before (they are not in currentSignedNetworkMap as
        // normal parameters or as an update)
        if (!latestNetworkParameters.isSigned) {
            signAndPersistNetworkParameters(latestNetworkParameters.networkParameters)
        } else {
            logger.debug { "No need to sign any network parameters as they're up-to-date" }
        }

        val parametersToNetworkMap = if (parametersUpdate?.status == FLAG_DAY || currentNetworkParametersHash == null) {
            parametersUpdate?.let { networkMapStorage.switchFlagDay(it) }
            SecureHash.parse(latestNetworkParameters.hash)
        } else {
            currentNetworkParametersHash
        }

        return Pair(parametersToNetworkMap, parametersUpdate?.let { if (it.status == NEW) it.toParametersUpdate() else null })
    }

    fun signAndPersistNetworkParameters(networkParameters: NetworkParameters) {
        networkMapStorage.saveNetworkParameters(networkParameters, signer.signObject(networkParameters).sig)
        logger.info("Signed network parameters: $networkParameters")
    }
}

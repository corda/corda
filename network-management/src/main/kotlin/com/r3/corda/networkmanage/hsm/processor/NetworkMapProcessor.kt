/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.processor

import com.r3.corda.networkmanage.common.persistence.PersistentNetworkMapStorage
import com.r3.corda.networkmanage.common.signer.NetworkMapSigner
import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import com.r3.corda.networkmanage.hsm.authentication.AuthMode
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.authentication.createProvider
import com.r3.corda.networkmanage.hsm.configuration.NetworkMapCertificateConfig
import com.r3.corda.networkmanage.hsm.signer.HsmSigner
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.CordaPersistence

class NetworkMapProcessor(private val config: NetworkMapCertificateConfig,
                          private val device: String,
                          private val keySpecifier: Int,
                          private val database: CordaPersistence) {
    companion object {
        val logger = contextLogger()
    }

    init {
        config.authParameters.run {
            requireNotNull(password)
            require(mode != AuthMode.CARD_READER)
            if (mode == AuthMode.KEY_FILE) {
                require(keyFilePath != null) { "Key file path cannot be null when authentication mode is ${AuthMode.KEY_FILE}" }
            }
        }
    }

    fun run() {
        logger.info("Starting network map processor.")
        config.run {
            val networkMapStorage = PersistentNetworkMapStorage(database)
            val signer = HsmSigner(
                    Authenticator(
                            AuthMode.KEY_FILE,
                            username,
                            authParameters.keyFilePath,
                            authParameters.password,
                            authParameters.threshold,
                            provider = createProvider(keyGroup, keySpecifier, device)),
                    keyName = CORDA_NETWORK_MAP)
            val networkMapSigner = NetworkMapSigner(networkMapStorage, signer)
            try {
                logger.info("Executing network map signing...")
                networkMapSigner.signNetworkMaps()
            } catch (e: Exception) {
                logger.error("Exception thrown while signing network map", e)
            }
        }
    }
}
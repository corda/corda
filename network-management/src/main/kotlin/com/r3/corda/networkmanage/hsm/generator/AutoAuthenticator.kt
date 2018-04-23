/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.generator

import CryptoServerJCE.CryptoServerProvider
import com.r3.corda.networkmanage.hsm.authentication.CryptoServerProviderConfig
import com.r3.corda.networkmanage.hsm.authentication.createProvider

/**
 * Performs user authentication against the HSM
 */
class AutoAuthenticator(providerConfig: CryptoServerProviderConfig,
                        private val userConfigs: List<UserAuthenticationParameters>) {
    private val provider = createProvider(providerConfig)

    /**
     * Interactively (using console) authenticates a user against the HSM. Once authentication is completed successfully
     * the [block] is executed.
     * @param block to be executed once the authentication process succeeds. The block should take a [CryptoServerProvider] instance as the parameter.
     */
    fun connectAndAuthenticate(block: (CryptoServerProvider) -> Unit) {
        try {
            for (userConfig in userConfigs) {
                when (userConfig.authMode) {
                    AuthMode.PASSWORD -> provider.loginPassword(userConfig.username, userConfig.authToken)
                    AuthMode.CARD_READER -> provider.loginSign(userConfig.username, ":cs2:cyb:USB0", null)
                    AuthMode.KEY_FILE -> provider.loginSign(userConfig.username, userConfig.keyFilePassword, userConfig.authToken)
                }
            }
            block(provider)
        } finally {
            try {
                provider.logoff()
            } catch (throwable: Throwable) {
                println("WARNING Exception while logging off")
                throwable.printStackTrace(System.out)
            }
        }
    }
}
/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.tools.shell

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.loggerFor
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.crsh.auth.AuthInfo
import org.crsh.auth.AuthenticationPlugin
import org.crsh.plugin.CRaSHPlugin

class CordaAuthenticationPlugin(private val rpcOps: (username: String, credential: String) -> CordaRPCOps): CRaSHPlugin<AuthenticationPlugin<String>>(), AuthenticationPlugin<String> {

    companion object {
        private val logger = loggerFor<CordaAuthenticationPlugin>()
    }

    override fun getImplementation(): AuthenticationPlugin<String> = this

    override fun getName(): String = "corda"

    override fun authenticate(username: String?, credential: String?): AuthInfo {

        if (username == null || credential == null) {
            return AuthInfo.UNSUCCESSFUL
        }
        try {
            val ops = rpcOps(username, credential)
            return CordaSSHAuthInfo(true, ops, isSsh = true)
        } catch (e: ActiveMQSecurityException) {
            logger.warn(e.message)
        } catch (e: Exception) {
            logger.warn(e.message, e)
        }
        return AuthInfo.UNSUCCESSFUL
    }

    override fun getCredentialType(): Class<String> = String::class.java
}
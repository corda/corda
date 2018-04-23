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

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.messaging.CordaRPCOps
import net.corda.tools.shell.InteractiveShell.createYamlInputMapper
import net.corda.tools.shell.utlities.ANSIProgressRenderer
import org.crsh.auth.AuthInfo

class CordaSSHAuthInfo(val successful: Boolean, val rpcOps: CordaRPCOps, val ansiProgressRenderer: ANSIProgressRenderer? = null, val isSsh: Boolean = false) : AuthInfo {
    override fun isSuccessful(): Boolean = successful

    val yamlInputMapper: ObjectMapper by lazy {
        createYamlInputMapper(rpcOps)
    }
}
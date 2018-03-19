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
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

fun makeRPCOps(getCordaRPCOps: (username: String, credential: String) -> CordaRPCOps, username: String, credential: String): CordaRPCOps {
    val cordaRPCOps: CordaRPCOps by lazy {
        getCordaRPCOps(username, credential)
    }

    return Proxy.newProxyInstance(CordaRPCOps::class.java.classLoader, arrayOf(CordaRPCOps::class.java), { _, method, args ->
        try {
            method.invoke(cordaRPCOps, *(args ?: arrayOf()))
        } catch (e: InvocationTargetException) {
            // Unpack exception.
            throw e.targetException
        }
    }) as CordaRPCOps
}

package net.corda.tools.shell

import net.corda.core.internal.messaging.InternalCordaRPCOps
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

fun makeRPCOps(getCordaRPCOps: (username: String, credential: String) -> InternalCordaRPCOps, username: String, credential: String): InternalCordaRPCOps {
    val cordaRPCOps: InternalCordaRPCOps by lazy {
        getCordaRPCOps(username, credential)
    }

    return Proxy.newProxyInstance(InternalCordaRPCOps::class.java.classLoader, arrayOf(InternalCordaRPCOps::class.java)) { _, method, args ->
        try {
            method.invoke(cordaRPCOps, *(args ?: arrayOf()))
        } catch (e: InvocationTargetException) {
            // Unpack exception.
            throw e.targetException
        }
    } as InternalCordaRPCOps
}

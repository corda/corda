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

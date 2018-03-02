package net.corda.tools.shell

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

fun makeRPCOps(getCordaRPCOps: (username: String, credential: String) -> CordaRPCOps, username: String, credential: String): CordaRPCOps {
    val cordaRPCOps: CordaRPCOps by lazy {
        getCordaRPCOps(username, credential)
    }

    return Proxy.newProxyInstance(CordaRPCOps::class.java.classLoader, arrayOf(CordaRPCOps::class.java), { _, method, args ->
        // TODO Simon check whether there's a reason for wanting to turn all non-blocking calls into blocking (apart from making the underlying proxy lazy)
//        RPCContextRunner {
        try {
            method.invoke(cordaRPCOps, *(args ?: arrayOf()))
        } catch (e: InvocationTargetException) {
            // Unpack exception.
            throw e.targetException
        }
    }
//    }
    ) as CordaRPCOps
}

private class RPCContextRunner<T>(val block: () -> T) : Thread() {

    private var result: CompletableFuture<T> = CompletableFuture()

    override fun run() {
        try {
            result.complete(block())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
        }
    }

    fun get(): Future<T> {
        start()
        join()
        return result
    }
}
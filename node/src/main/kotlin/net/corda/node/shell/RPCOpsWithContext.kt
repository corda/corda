package net.corda.node.shell

import net.corda.core.context.InvocationContext
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.security.AuthorizingSubject
import net.corda.node.services.messaging.CURRENT_RPC_CONTEXT
import net.corda.node.services.messaging.RpcAuthContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

fun makeRPCOpsWithContext(getCordaRPCOps: (username: String?, credential: String?) -> CordaRPCOps, invocationContext:InvocationContext, authorizingSubject: AuthorizingSubject, username: String?, credential: String?) : CordaRPCOps {
    val cordaRPCOps: CordaRPCOps by lazy {
        getCordaRPCOps(username, credential)
    }

    return Proxy.newProxyInstance(CordaRPCOps::class.java.classLoader, arrayOf(CordaRPCOps::class.java), { _, method, args ->
        RPCContextRunner(invocationContext, authorizingSubject) {
            try {
                 method.invoke(cordaRPCOps, *(args ?: arrayOf()))
            } catch (e: InvocationTargetException) {
                // Unpack exception.
                throw e.targetException
            }
        }.get().getOrThrow()
    }) as CordaRPCOps
}

private class RPCContextRunner<T>(val invocationContext: InvocationContext, val authorizingSubject: AuthorizingSubject, val block:() -> T): Thread() {

    private var result: CompletableFuture<T> = CompletableFuture()

    override fun run() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(invocationContext, authorizingSubject))
        try {
            result.complete(block())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
        } finally {
            CURRENT_RPC_CONTEXT.remove()
        }
    }

    fun get(): Future<T> {
        start()
        join()
        return result
    }
}
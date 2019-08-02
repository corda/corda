package net.corda.node.shell

import net.corda.core.context.InvocationContext
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.security.AuthorizingSubject
import net.corda.node.services.messaging.CURRENT_RPC_CONTEXT
import net.corda.node.services.messaging.RpcAuthContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

fun makeRPCOpsWithContext(cordaRPCOps: InternalCordaRPCOps, invocationContext:InvocationContext, authorizingSubject: AuthorizingSubject) : InternalCordaRPCOps {

    return Proxy.newProxyInstance(InternalCordaRPCOps::class.java.classLoader, arrayOf(InternalCordaRPCOps::class.java), { _, method, args ->
        RPCContextRunner(invocationContext, authorizingSubject) {
            try {
                method.invoke(cordaRPCOps, *(args ?: arrayOf()))
            } catch (e: InvocationTargetException) {
                // Unpack exception.
                throw e.targetException
            }
        }.get().getOrThrow()
    }) as InternalCordaRPCOps
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
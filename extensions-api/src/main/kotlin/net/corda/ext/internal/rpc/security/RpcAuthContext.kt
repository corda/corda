package net.corda.ext.internal.rpc.security

import net.corda.core.context.InvocationContext
import net.corda.ext.internal.logging.context.pushToLoggingContext
import org.slf4j.MDC

data class RpcAuthContext(val invocation: InvocationContext,
                          private val authorizer: AuthorizingSubject)
    : AuthorizingSubject by authorizer


@JvmField
val CURRENT_RPC_CONTEXT: ThreadLocal<RpcAuthContext> = CurrentRpcContext()

/**
 * Returns a context specific to the current RPC call. Note that trying to call this function outside of an RPC will
 * throw. If you'd like to use the context outside of the call (e.g. in another thread) then pass the returned reference
 * around explicitly.
 * The [RpcAuthContext] includes permissions.
 */
fun rpcContext(): RpcAuthContext = CURRENT_RPC_CONTEXT.get()

internal class CurrentRpcContext : ThreadLocal<RpcAuthContext>() {

    override fun remove() {
        super.remove()
        MDC.clear()
    }

    override fun set(context: RpcAuthContext?) {
        when {
            context != null -> {
                super.set(context)
                // this is needed here as well because the Shell sets the context without going through the RpcServer
                context.invocation.pushToLoggingContext()
            }
            else -> remove()
        }
    }
}
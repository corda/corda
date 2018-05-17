package net.corda.node.internal

import net.corda.client.rpc.PermissionException
import net.corda.core.flows.FlowLogic
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.services.messaging.RpcAuthContext
import net.corda.node.services.messaging.rpcContext
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Implementation of [CordaRPCOps] that checks authorisation.
 */
class AuthenticatedRpcOpsProxy(private val delegate: CordaRPCOps) : CordaRPCOps by proxy(delegate, ::rpcContext) {

    /**
     * Returns the RPC protocol version, which is the same the node's Platform Version. Exists since version 1 so guaranteed
     * to be present.
     */
    override val protocolVersion: Int get() = delegate.nodeInfo().platformVersion

    override fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?) = guard("startFlowDynamic", listOf(logicType), ::rpcContext) {
        delegate.startFlowDynamic(logicType, *args)
    }

    override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?) = guard("startTrackedFlowDynamic", listOf(logicType), ::rpcContext) {
        delegate.startTrackedFlowDynamic(logicType, *args)
    }

    private companion object {
        private fun proxy(delegate: CordaRPCOps, context: () -> RpcAuthContext): CordaRPCOps {

            val handler = PermissionsEnforcingInvocationHandler(delegate, context)
            return Proxy.newProxyInstance(delegate::class.java.classLoader, arrayOf(CordaRPCOps::class.java), handler) as CordaRPCOps
        }
    }

    private class PermissionsEnforcingInvocationHandler(override val delegate: CordaRPCOps, private val context: () -> RpcAuthContext) : InvocationHandlerTemplate {
        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?) = guard(method.name, context, { super.invoke(proxy, method, arguments) })
    }
}

private inline fun <RESULT> guard(methodName: String, context: () -> RpcAuthContext, action: () -> RESULT) = guard(methodName, emptyList(), context, action)

private inline fun <RESULT> guard(methodName: String, args: List<Class<*>>, context: () -> RpcAuthContext, action: () -> RESULT): RESULT {
    if (!context().isPermitted(methodName, *(args.map { it.name }.toTypedArray()))) {
        throw PermissionException("User not authorized to perform RPC call $methodName with target $args")
    } else {
        return action()
    }
}
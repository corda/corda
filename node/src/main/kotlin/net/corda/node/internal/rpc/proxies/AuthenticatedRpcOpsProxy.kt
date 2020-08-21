package net.corda.node.internal.rpc.proxies

import net.corda.client.rpc.PermissionException
import net.corda.core.internal.utilities.InvocationHandlerTemplate
import net.corda.core.messaging.RPCOps
import net.corda.node.internal.rpc.proxies.RpcAuthHelper.methodFullName
import net.corda.node.services.rpc.RpcAuthContext
import net.corda.node.services.rpc.rpcContext
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Creates proxy that checks entitlements for every RPCOps interface call.
 */
internal object AuthenticatedRpcOpsProxy {

    fun <T : RPCOps> proxy(delegate: T, targetInterface: Class<out T>): T {
        require(targetInterface.isInterface) { "Interface is expected instead of $targetInterface" }
        val handler = PermissionsEnforcingInvocationHandler(delegate, targetInterface)
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(delegate::class.java.classLoader, arrayOf(targetInterface), handler) as T
    }

    private class PermissionsEnforcingInvocationHandler(override val delegate: Any, private val clazz: Class<*>) : InvocationHandlerTemplate {

        private val exemptMethod = RPCOps::class.java.getMethod("getProtocolVersion")

        private val namedInterfaces = setOf(
                net.corda.core.messaging.CordaRPCOps::class.java)
        private val namedMethods = setOf("startFlowDynamic", "startTrackedFlowDynamic")

        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {

            if (method == exemptMethod) {
                // "getProtocolVersion" is an exempt from entitlements check as this is the very first *any* RPCClient calls upon login
                return super.invoke(proxy, method, arguments)
            }

            val importantArgs = if (clazz in namedInterfaces && method.name in namedMethods) {
                // Normally list of arguments makes no difference when checking entitlements, however when starting flows
                // first argument represents a class name of the flow to be started and special handling applies in this case with
                // name of the class extracted and passed into `guard` method for entitlements check.
                val nonNullArgs = requireNotNull(arguments)
                require(nonNullArgs.isNotEmpty())
                val firstArgClass = requireNotNull(nonNullArgs[0] as? Class<*>)
                listOf(firstArgClass)
            } else emptyList()

            return guard(method, importantArgs, ::rpcContext) { super.invoke(proxy, method, arguments) }
        }

        private fun <RESULT> guard(method: Method, args: List<Class<*>>, context: () -> RpcAuthContext, action: () -> RESULT): RESULT {
            if (!context().isPermitted(methodFullName(method), *(args.map(Class<*>::getName).toTypedArray()))) {
                throw PermissionException("User not authorized to perform RPC call $method with target $args")
            } else {
                return action()
            }
        }
    }
}

object RpcAuthHelper {
    const val INTERFACE_SEPARATOR = "#"

    fun methodFullName(method: Method):String = methodFullName(method.declaringClass, method.name)

    fun methodFullName(clazz: Class<*>, methodName: String): String {
        require(clazz.isInterface) { "Must be an interface: $clazz"}
        require(RPCOps::class.java.isAssignableFrom(clazz)) { "Must be assignable from RPCOps: $clazz" }
        return clazz.name + INTERFACE_SEPARATOR + methodName
    }
}
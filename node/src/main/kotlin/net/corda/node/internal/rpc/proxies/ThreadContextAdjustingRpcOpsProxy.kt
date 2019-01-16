package net.corda.node.internal.rpc.proxies

import net.corda.core.internal.executeWithThreadContextClassLoader
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.internal.InvocationHandlerTemplate
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * A [CordaRPCOps] proxy that adjusts the thread context's class loader temporarily on every invocation with the provided classloader.
 * As an example, this can be used to work-around cases, where 3rd party libraries prioritise the thread context's class loader over the current one,
 * without sensible fallbacks to the classloader of the current instance.
 * If clients' CorDapps use one of these libraries, this temporary adjustment can ensure that any provided classes from these libraries will be available during RPC calls.
 */
internal class ThreadContextAdjustingRpcOpsProxy(private val delegate: CordaRPCOps, private val classLoader: ClassLoader): CordaRPCOps by proxy(delegate, classLoader) {
    private companion object {
        private fun proxy(delegate: CordaRPCOps, classLoader: ClassLoader): CordaRPCOps {
            val handler = ThreadContextAdjustingRpcOpsProxy.ThreadContextAdjustingInvocationHandler(delegate, classLoader)
            return Proxy.newProxyInstance(delegate::class.java.classLoader, arrayOf(CordaRPCOps::class.java), handler) as CordaRPCOps
        }
    }

    private class ThreadContextAdjustingInvocationHandler(override val delegate: CordaRPCOps, private val classLoader: ClassLoader) : InvocationHandlerTemplate {
        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
            return executeWithThreadContextClassLoader(this.classLoader) { super.invoke(proxy, method, arguments) }
        }
    }

}
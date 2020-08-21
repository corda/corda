package net.corda.node.internal.rpc.proxies

import net.corda.core.internal.executeWithThreadContextClassLoader
import net.corda.core.messaging.RPCOps
import net.corda.core.internal.utilities.InvocationHandlerTemplate
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * A proxy that adjusts the thread context's class loader temporarily on every invocation of supplied interface with the provided classloader.
 * As an example, this can be used to work-around cases, where 3rd party libraries prioritise the thread context's class loader over the current one,
 * without sensible fallbacks to the classloader of the current instance.
 * If clients' CorDapps use one of these libraries, this temporary adjustment can ensure that any provided classes from these libraries will be available during RPC calls.
 */
internal object ThreadContextAdjustingRpcOpsProxy {
    fun <T : RPCOps> proxy(delegate: T, clazz: Class<out T>, classLoader: ClassLoader): T {
        require(clazz.isInterface) { "Interface is expected instead of $clazz" }
        val handler = ThreadContextAdjustingInvocationHandler(delegate, classLoader)
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(delegate::class.java.classLoader, arrayOf(clazz), handler) as T
    }

    internal class ThreadContextAdjustingInvocationHandler(override val delegate: Any, private val classLoader: ClassLoader) : InvocationHandlerTemplate {
        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
            return executeWithThreadContextClassLoader(this.classLoader) { super.invoke(proxy, method, arguments) }
        }
    }
}
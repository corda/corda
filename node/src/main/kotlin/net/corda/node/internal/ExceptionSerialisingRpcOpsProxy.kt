package net.corda.node.internal

import net.corda.core.CordaRuntimeException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.CordaSerializable
import java.lang.reflect.Method
import java.lang.reflect.Proxy.newProxyInstance

internal class ExceptionSerialisingRpcOpsProxy(private val delegate: CordaRPCOps) : CordaRPCOps by proxy(delegate) {
    private companion object {
        private fun proxy(delegate: CordaRPCOps): CordaRPCOps {
            val handler = ErrorSerialisingInvocationHandler(delegate)
            return newProxyInstance(delegate::class.java.classLoader, arrayOf(CordaRPCOps::class.java), handler) as CordaRPCOps
        }
    }

    private class ErrorSerialisingInvocationHandler(override val delegate: CordaRPCOps) : InvocationHandlerTemplate {
        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
            try {
                return super.invoke(proxy, method, arguments)
            } catch (exception: Exception) {
                throw ensureSerialisable(exception)
            }
        }

        private fun ensureSerialisable(error: Throwable): Throwable {

            val serialisable = (superclasses(error::class.java) + error::class.java.interfaces).any { it.isAnnotationPresent(CordaSerializable::class.java) }
            return if (serialisable) error else CordaRuntimeException(error.message, error)
        }

        private fun superclasses(clazz: Class<*>): List<Class<*>> {
            val superclasses = mutableListOf<Class<*>>()
            var current: Class<*>?
            var superclass = clazz.superclass
            while (superclass != null) {
                superclasses += superclass
                current = superclass
                superclass = current?.superclass
            }
            return superclasses
        }
    }

    override fun toString(): String {
        return "ExceptionSerialisingRpcOpsProxy"
    }
}
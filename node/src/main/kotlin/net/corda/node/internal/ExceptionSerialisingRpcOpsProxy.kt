package net.corda.node.internal

import net.corda.core.CordaRuntimeException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.CordaSerializable
import java.lang.reflect.Method
import java.lang.reflect.Proxy.newProxyInstance
import kotlin.reflect.full.findAnnotation

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
            return error::cause.findAnnotation<CordaSerializable>()?.let { error } ?: CordaRuntimeException(error.message, error)
        }
    }

    override fun toString(): String {
        return "ExceptionSerialisingRpcOpsProxy"
    }
}
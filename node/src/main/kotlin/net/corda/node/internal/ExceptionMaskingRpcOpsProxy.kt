package net.corda.node.internal

import net.corda.core.CordaThrowable
import net.corda.core.concurrent.CordaFuture
import net.corda.core.doOnError
import net.corda.core.internal.concurrent.doOnError
import net.corda.core.internal.concurrent.mapError
import net.corda.core.mapErrors
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowHandleImpl
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.core.utilities.loggerFor
import rx.Observable
import java.lang.reflect.Method
import java.lang.reflect.Proxy.newProxyInstance

internal class ExceptionMaskingRpcOpsProxy(private val delegate: CordaRPCOps) : CordaRPCOps by proxy(delegate) {

    private companion object {
        private val logger = loggerFor<ExceptionMaskingRpcOpsProxy>()

        private fun proxy(delegate: CordaRPCOps): CordaRPCOps {
            val handler = ErrorObfuscatingInvocationHandler(delegate)
            return newProxyInstance(delegate::class.java.classLoader, arrayOf(CordaRPCOps::class.java), handler) as CordaRPCOps
        }
    }

    private class ErrorObfuscatingInvocationHandler(override val delegate: CordaRPCOps) : InvocationHandlerTemplate {
        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
            try {
                val result = super.invoke(proxy, method, arguments)
                return result?.let { obfuscateResult(it) }
            } catch (exception: Exception) {
                // In this special case logging and re-throwing is the right approach.
                log(exception)
                throw obfuscate(exception)
            }
        }

        private fun <RESULT : Any> obfuscateResult(result: RESULT): Any {
            return when (result) {
                is CordaFuture<*> -> wrapFuture(result)
                is DataFeed<*, *> -> wrapFeed(result)
                is FlowProgressHandle<*> -> wrapFlowProgressHandle(result)
                is FlowHandle<*> -> wrapFlowHandle(result)
                is Observable<*> -> wrapObservable(result)
                else -> result
            }
        }

        private fun wrapFlowProgressHandle(handle: FlowProgressHandle<*>): FlowProgressHandle<*> {
            val returnValue = wrapFuture(handle.returnValue)
            val progress = wrapObservable(handle.progress)
            val stepsTreeIndexFeed = handle.stepsTreeIndexFeed?.let { wrapFeed(it) }
            val stepsTreeFeed = handle.stepsTreeFeed?.let { wrapFeed(it) }

            return FlowProgressHandleImpl(handle.id, returnValue, progress, stepsTreeIndexFeed, stepsTreeFeed)
        }

        private fun wrapFlowHandle(handle: FlowHandle<*>): FlowHandle<*> {
            return FlowHandleImpl(handle.id, wrapFuture(handle.returnValue))
        }

        private fun <ELEMENT> wrapObservable(observable: Observable<ELEMENT>): Observable<ELEMENT> {
            return observable.doOnError(::log).mapErrors(::obfuscate)
        }

        private fun <SNAPSHOT, ELEMENT> wrapFeed(feed: DataFeed<SNAPSHOT, ELEMENT>): DataFeed<SNAPSHOT, ELEMENT> {
            return feed.doOnError(::log).mapErrors(::obfuscate)
        }

        private fun <RESULT> wrapFuture(future: CordaFuture<RESULT>): CordaFuture<RESULT> {
            return future.doOnError(::log).mapError(::obfuscate)
        }

        private fun log(error: Throwable) {
            logger.error("Error during RPC invocation", error)
        }

        private fun obfuscate(error: Throwable): Throwable {
            removeDetails(error)
            return error
        }

        private fun removeDetails(error: Throwable) {
            if (error is CordaThrowable) {
                error.stackTrace = arrayOf<StackTraceElement>()
                error.setCause(null)
            }
        }

        override fun toString(): String {
            return "ErrorObfuscatingInvocationHandler"
        }
    }

    override fun toString(): String {
        return "ExceptionMaskingRpcOpsProxy"
    }
}
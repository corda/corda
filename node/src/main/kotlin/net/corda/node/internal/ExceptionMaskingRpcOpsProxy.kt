package net.corda.node.internal

import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.doOnError
import net.corda.core.flows.ClientRelevantError
import net.corda.core.flows.ContextAware
import net.corda.core.internal.concurrent.doOnError
import net.corda.core.internal.concurrent.mapError
import net.corda.core.mapErrors
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowHandleImpl
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.core.setFieldToNull
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.exceptions.InternalNodeException
import rx.Observable
import java.io.InvalidClassException
import java.lang.reflect.Method
import java.lang.reflect.Proxy.newProxyInstance
import kotlin.reflect.KClass

// TODO sollecitom make this enterprise only as discussed with Mike
internal class ExceptionMaskingRpcOpsProxy(private val delegate: CordaRPCOps) : CordaRPCOps by proxy(delegate) {

    private companion object {
        private val logger = loggerFor<ExceptionMaskingRpcOpsProxy>()

        private val whitelist = setOf(
                ClientRelevantError::class,
                InvalidClassException::class,
                TransactionVerificationException::class
        )

        private fun proxy(delegate: CordaRPCOps): CordaRPCOps {
            val handler = ErrorObfuscatingInvocationHandler(delegate, whitelist)
            return newProxyInstance(delegate::class.java.classLoader, arrayOf(CordaRPCOps::class.java), handler) as CordaRPCOps
        }
    }

    private class ErrorObfuscatingInvocationHandler(override val delegate: CordaRPCOps, private val whitelist: Set<KClass<*>>) : InvocationHandlerTemplate {
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
            val additionalContext = if (error is ContextAware) error.additionalContext else emptyMap()
            val exposed = if (error.isWhitelisted()) error else InternalNodeException(additionalContext)
            removeDetails(exposed)
            return exposed
        }

        private fun removeDetails(error: Throwable) {
            error.stackTrace = arrayOf<StackTraceElement>()
            error.setFieldToNull("cause")
            error.setFieldToNull("suppressedExceptions")
            when (error) {
                is CordaException -> error.setCause(null)
                is CordaRuntimeException -> error.setCause(null)
            }
        }

        private fun Throwable.isWhitelisted(): Boolean {
            return whitelist.any { it.isInstance(this) }
        }

        override fun toString(): String {
            return "ErrorObfuscatingInvocationHandler(whitelist=$whitelist)"
        }
    }

    override fun toString(): String {
        return "ExceptionMaskingRpcOpsProxy"
    }
}
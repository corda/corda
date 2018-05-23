/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.rpc.proxies

import net.corda.core.ClientRelevantError
import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.doOnError
import net.corda.core.flows.IdentifiableException
import net.corda.core.internal.concurrent.doOnError
import net.corda.core.internal.concurrent.mapError
import net.corda.core.internal.declaredField
import net.corda.core.mapErrors
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowHandleImpl
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.InvocationHandlerTemplate
import net.corda.nodeapi.exceptions.InternalNodeException
import rx.Observable
import java.lang.reflect.Method
import java.lang.reflect.Proxy.newProxyInstance
import kotlin.reflect.KClass

internal class ExceptionMaskingRpcOpsProxy(private val delegate: CordaRPCOps, doLog: Boolean) : CordaRPCOps by proxy(delegate, doLog) {
    private companion object {
        private val logger = loggerFor<ExceptionMaskingRpcOpsProxy>()

        private val whitelist = setOf(
                ClientRelevantError::class,
                TransactionVerificationException::class
        )

        private fun proxy(delegate: CordaRPCOps, doLog: Boolean): CordaRPCOps {
            val handler = ErrorObfuscatingInvocationHandler(delegate, whitelist, doLog)
            return newProxyInstance(delegate::class.java.classLoader, arrayOf(CordaRPCOps::class.java), handler) as CordaRPCOps
        }
    }

    private class ErrorObfuscatingInvocationHandler(override val delegate: CordaRPCOps, private val whitelist: Set<KClass<*>>, private val doLog: Boolean) : InvocationHandlerTemplate {
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
            if (doLog) {
                logger.error("Error during RPC invocation", error)
            }
        }

        private fun obfuscate(error: Throwable): Throwable {
            val exposed = if (error.isWhitelisted()) error else InternalNodeException((error as? IdentifiableException)?.errorId)
            removeDetails(exposed)
            return exposed
        }

        private fun removeDetails(error: Throwable) {
            error.stackTrace = arrayOf<StackTraceElement>()
            error.declaredField<Any?>("cause").value = null
            error.declaredField<Any?>("suppressedExceptions").value = null
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
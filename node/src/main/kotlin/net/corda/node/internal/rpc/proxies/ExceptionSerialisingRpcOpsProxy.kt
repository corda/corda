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

import net.corda.core.CordaRuntimeException
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
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.InvocationHandlerTemplate
import rx.Observable
import java.lang.reflect.Method
import java.lang.reflect.Proxy.newProxyInstance

internal class ExceptionSerialisingRpcOpsProxy(private val delegate: CordaRPCOps, doLog: Boolean) : CordaRPCOps by proxy(delegate, doLog) {
    private companion object {
        private val logger = loggerFor<ExceptionSerialisingRpcOpsProxy>()

        private fun proxy(delegate: CordaRPCOps, doLog: Boolean): CordaRPCOps {
            val handler = ErrorSerialisingInvocationHandler(delegate, doLog)
            return newProxyInstance(delegate::class.java.classLoader, arrayOf(CordaRPCOps::class.java), handler) as CordaRPCOps
        }
    }

    private class ErrorSerialisingInvocationHandler(override val delegate: CordaRPCOps, private val doLog: Boolean) : InvocationHandlerTemplate {
        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
            try {
                val result = super.invoke(proxy, method, arguments)
                return result?.let { ensureSerialisable(it) }
            } catch (exception: Exception) {
                throw ensureSerialisable(exception)
            }
        }

        private fun <RESULT : Any> ensureSerialisable(result: RESULT): Any {
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
            return observable.doOnError(::log).mapErrors(::ensureSerialisable)
        }

        private fun <SNAPSHOT, ELEMENT> wrapFeed(feed: DataFeed<SNAPSHOT, ELEMENT>): DataFeed<SNAPSHOT, ELEMENT> {
            return feed.doOnError(::log).mapErrors(::ensureSerialisable)
        }

        private fun <RESULT> wrapFuture(future: CordaFuture<RESULT>): CordaFuture<RESULT> {
            return future.doOnError(::log).mapError(::ensureSerialisable)
        }

        private fun ensureSerialisable(error: Throwable): Throwable {
            val serialisable = (superclasses(error::class.java) + error::class.java).any { it.isAnnotationPresent(CordaSerializable::class.java) || it.interfaces.any { it.isAnnotationPresent(CordaSerializable::class.java) } }
            val result = if (serialisable) {
                error
            } else {
                log(error)
                CordaRuntimeException(error.message, error)
            }

            if (result is CordaThrowable) {
                result.stackTrace = arrayOf<StackTraceElement>()
                result.setCause(null)
            }
            return result
        }

        private fun log(error: Throwable) {
            if (doLog) {
                logger.error("Error during RPC invocation", error)
            }
        }

        private fun superclasses(clazz: Class<*>): List<Class<*>> {
            val superclasses = mutableListOf<Class<*>>()
            var current: Class<*>?
            var superclass = clazz.superclass
            while (superclass != null) {
                superclasses += superclass
                current = superclass
                superclass = current.superclass
            }
            return superclasses
        }
    }

    override fun toString(): String {
        return "ExceptionSerialisingRpcOpsProxy"
    }
}
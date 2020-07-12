package net.corda.coretesting.internal.matchers.flow

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import net.corda.core.internal.FlowStateMachineHandle
import net.corda.coretesting.internal.matchers.*

/**
 * Matches a Flow that succeeds with a result matched by the given matcher
 */
fun <T> willReturn(): Matcher<FlowStateMachineHandle<T>> = net.corda.coretesting.internal.matchers.future.willReturn<T>()
        .extrude(FlowStateMachineHandle<T>::resultFuture)
        .redescribe { "is a flow that will return" }

fun <T> willReturn(expected: T): Matcher<FlowStateMachineHandle<T>> = willReturn(equalTo(expected))

/**
 * Matches a Flow that succeeds with a result matched by the given matcher
 */
fun <T> willReturn(successMatcher: Matcher<T>) = net.corda.coretesting.internal.matchers.future.willReturn(successMatcher)
        .extrude(FlowStateMachineHandle<out T>::resultFuture)
        .redescribe { "is a flow that will return with a value that ${successMatcher.description}" }

/**
 * Matches a Flow that fails, with an exception matched by the given matcher.
 */
inline fun <reified E: Exception> willThrow(failureMatcher: Matcher<E>) =
        net.corda.coretesting.internal.matchers.future.willThrow(failureMatcher)
            .extrude(FlowStateMachineHandle<*>::resultFuture)
            .redescribe { "is a flow that will fail, throwing an exception that ${failureMatcher.description}" }

/**
 * Matches a Flow that fails, with an exception of the specified type.
 */
inline fun <reified E: Exception> willThrow() =
        net.corda.coretesting.internal.matchers.future.willThrow<E>()
                .extrude(FlowStateMachineHandle<*>::resultFuture)
                .redescribe { "is a flow that will fail with an exception of type ${E::class.java.simpleName}" }
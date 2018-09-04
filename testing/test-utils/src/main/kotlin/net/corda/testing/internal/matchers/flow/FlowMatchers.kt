package net.corda.testing.internal.matchers.flow

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import net.corda.core.internal.FlowStateMachine
import net.corda.testing.internal.matchers.*

/**
 * Matches a Flow that succeeds with a result matched by the given matcher
 */
fun <T> willReturn(): Matcher<FlowStateMachine<T>> = net.corda.testing.internal.matchers.future.willReturn<T>()
        .extrude(FlowStateMachine<T>::resultFuture)
        .redescribe { "is a flow that will return" }

fun <T> willReturn(expected: T): Matcher<FlowStateMachine<T>> = willReturn(equalTo(expected))

/**
 * Matches a Flow that succeeds with a result matched by the given matcher
 */
fun <T> willReturn(successMatcher: Matcher<T>) = net.corda.testing.internal.matchers.future.willReturn(successMatcher)
        .extrude(FlowStateMachine<out T>::resultFuture)
        .redescribe { "is a flow that will return with a value that ${successMatcher.description}" }

/**
 * Matches a Flow that fails, with an exception matched by the given matcher.
 */
inline fun <reified E: Exception> willThrow(failureMatcher: Matcher<E>) =
        net.corda.testing.internal.matchers.future.willThrow(failureMatcher)
            .extrude(FlowStateMachine<*>::resultFuture)
            .redescribe { "is a flow that will fail, throwing an exception that ${failureMatcher.description}" }

/**
 * Matches a Flow that fails, with an exception of the specified type.
 */
inline fun <reified E: Exception> willThrow() =
        net.corda.testing.internal.matchers.future.willThrow<E>()
                .extrude(FlowStateMachine<*>::resultFuture)
                .redescribe { "is a flow that will fail with an exception of type ${E::class.java.simpleName}" }
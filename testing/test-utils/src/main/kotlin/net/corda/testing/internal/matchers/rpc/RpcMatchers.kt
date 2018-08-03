package net.corda.testing.internal.matchers.rpc

import com.natpryce.hamkrest.Matcher
import net.corda.core.messaging.FlowHandle
import net.corda.testing.internal.matchers.extrude
import net.corda.testing.internal.matchers.redescribe

/**
 * Matches a flow handle that succeeds with a result matched by the given matcher
 */
fun <T> willReturn() = net.corda.testing.internal.matchers.future.willReturn<T>()
        .extrude(FlowHandle<T>::returnValue)
        .redescribe { "is an RPG flow handle that will return" }

/**
 * Matches a flow handle that succeeds with a result matched by the given matcher
 */
fun <T> willReturn(successMatcher: Matcher<T>) = net.corda.testing.internal.matchers.future.willReturn(successMatcher)
        .extrude(FlowHandle<T>::returnValue)
        .redescribe { "is an RPG flow handle that will return a value that ${successMatcher.description}" }

/**
 * Matches a flow handle that fails, with an exception matched by the given matcher.
 */
inline fun <reified E: Exception> willThrow(failureMatcher: Matcher<E>) =
        net.corda.testing.internal.matchers.future.willThrow(failureMatcher)
        .extrude(FlowHandle<*>::returnValue)
        .redescribe { "is an RPG flow handle that will fail with an exception that ${failureMatcher.description}" }

/**
 * Matches a flow handle that fails, with an exception of the specified type.
 */
inline fun <reified E: Exception> willThrow() =
        net.corda.testing.internal.matchers.future.willThrow<E>()
        .extrude(FlowHandle<*>::returnValue)
        .redescribe { "is an RPG flow handle that will fail with an exception of type ${E::class.java.simpleName}" }
package net.corda.core.flows.matchers.rpc

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.has
import net.corda.core.flows.matchers.willThrow
import net.corda.core.flows.matchers.willFailWithException
import net.corda.core.flows.matchers.willReturn
import net.corda.core.flows.matchers.willSucceedWithResult
import net.corda.core.messaging.FlowHandle

/**
 * Matches a flow handle that succeeds with a result matched by the given matcher
 */
fun <T> willReturn() = has(FlowHandle<T>::returnValue, willReturn())

/**
 * Matches a flow handle that succeeds with a result matched by the given matcher
 */
fun <T> willReturn(successMatcher: Matcher<T>) = has(FlowHandle<out T>::returnValue, willSucceedWithResult(successMatcher))

/**
 * Matches a flow handle that fails, with an exception matched by the given matcher.
 */
inline fun <reified E: Exception> willThrow(failureMatcher: Matcher<E>) = has(
    FlowHandle<*>::returnValue,
    willFailWithException(failureMatcher))

/**
 * Matches a flow handle that fails, with an exception of the specified type.
 */
inline fun <reified E: Exception> willThrow() = has(
    FlowHandle<*>::returnValue,
        willThrow<E>())
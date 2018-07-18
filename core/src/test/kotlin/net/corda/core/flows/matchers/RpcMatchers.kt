package net.corda.core.flows.matchers

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import net.corda.core.messaging.FlowHandle
import net.corda.core.utilities.getOrThrow

/**
 * Matches an RPC flow handle that succeeds with a result matched by the given matcher
 */
fun <T> rpcSucceeds() = object : Matcher<FlowHandle<T>> {
    override val description: String = "is an RPC flow handle that succeeds"

    override fun invoke(actual: FlowHandle<T>): MatchResult = try {
        actual.returnValue.getOrThrow()
        MatchResult.Match
    } catch (e: Exception) {
        MatchResult.Mismatch("Failed with $e")
    }
}

/**
 * Matches an RPC flow handle that succeeds with a result matched by the given matcher
 */
fun <T> rpcSucceedsWith(successMatcher: Matcher<T>) = object : Matcher<FlowHandle<T>> {
    override val description: String = "is an RPC flow handle that succeeds with a value that ${successMatcher.description}"

    override fun invoke(actual: FlowHandle<T>): MatchResult = try {
        successMatcher(actual.returnValue.getOrThrow())
    } catch (e: Exception) {
        MatchResult.Mismatch("Failed with $e")
    }
}

/**
 * Matches an RPC Flow handle that fails, with an exception matched by the given matcher.
 */
inline fun <reified E: Exception> rpcFails(failureMatcher: Matcher<E>) = object : Matcher<FlowHandle<*>> {
    override val description: String
        get() = "is an RPC flow handle that fails with a ${E::class.java.simpleName} that ${failureMatcher.description}"

    override fun invoke(actual: FlowHandle<*>): MatchResult = try {
        actual.returnValue.getOrThrow()
        MatchResult.Mismatch("Succeeded")
    } catch (e: Exception) {
        when(e) {
            is E -> failureMatcher(e)
            else -> MatchResult.Mismatch("Failure class was ${e.javaClass}")
        }
    }
}

/**
 * Matches a Flow that fails, with an exception of the specified type.
 */
inline fun <reified E: Exception> rpcFails() = object : Matcher<FlowHandle<*>> {
    override val description: String
        get() = "is an RPC flow handle that fails with a ${E::class.java}"

    override fun invoke(actual: FlowHandle<*>): MatchResult = try {
        actual.returnValue.getOrThrow()
        MatchResult.Mismatch("Succeeded")
    } catch (e: Exception) {
        when(e) {
            is E -> MatchResult.Match
            else -> MatchResult.Mismatch("Failure class was ${e.javaClass}")
        }
    }
}
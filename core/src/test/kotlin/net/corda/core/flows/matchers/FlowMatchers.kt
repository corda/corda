package net.corda.core.flows.matchers

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import net.corda.core.internal.FlowStateMachine
import net.corda.core.utilities.getOrThrow

/**
 * Matches a Flow that succeeds with a result matched by the given matcher
 */
fun <T> succeeds() = object : Matcher<FlowStateMachine<T>> {
    override val description: String = "is a flow that succeeds"

    override fun invoke(actual: FlowStateMachine<T>): MatchResult = try {
        actual.resultFuture.getOrThrow()
        MatchResult.Match
    } catch (e: Exception) {
        MatchResult.Mismatch("Failed with $e")
    }
}

/**
 * Matches a Flow that succeeds with a result matched by the given matcher
 */
fun <T> succeedsWith(successMatcher: Matcher<T>) = object : Matcher<FlowStateMachine<out T>> {
    override val description: String = "is a flow that succeeds with a value that ${successMatcher.description}"

    override fun invoke(actual: FlowStateMachine<out T>): MatchResult = try {
        successMatcher(actual.resultFuture.getOrThrow())
    } catch (e: Exception) {
        MatchResult.Mismatch("Failed with $e")
    }
}

/**
 * Matches a Flow that fails, with an exception matched by the given matcher.
 */
inline fun <reified E: Exception> fails(failureMatcher: Matcher<E>) = object : Matcher<FlowStateMachine<*>> {
    override val description: String
        get() = "is a flow that fails with a ${E::class.java.simpleName} that ${failureMatcher.description}"

    override fun invoke(actual: FlowStateMachine<*>): MatchResult = try {
        actual.resultFuture.getOrThrow()
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
inline fun <reified E: Exception> fails() = object : Matcher<FlowStateMachine<*>> {
    override val description: String
        get() = "is a flow that fails with a ${E::class.java}"

    override fun invoke(actual: FlowStateMachine<*>): MatchResult = try {
        actual.resultFuture.getOrThrow()
        MatchResult.Mismatch("Succeeded")
    } catch (e: Exception) {
        when(e) {
            is E -> MatchResult.Match
            else -> MatchResult.Mismatch("Failure class was ${e.javaClass}")
        }
    }
}
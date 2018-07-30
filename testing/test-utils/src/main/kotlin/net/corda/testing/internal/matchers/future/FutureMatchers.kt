package net.corda.testing.internal.matchers.future

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.matchers.modifyMismatchDescription
import java.util.concurrent.Future

/**
 * Matches a Flow that succeeds with a result matched by the given matcher
 */
fun <T> willReturn() = object : Matcher<Future<T>> {
    override val description: String = "is a future that will succeed"

    override fun invoke(actual: Future<T>): MatchResult = try {
        actual.getOrThrow()
        MatchResult.Match
    } catch (e: Exception) {
        MatchResult.Mismatch("failed with $e")
    }
}

fun <T> willReturn(expected: T): Matcher<Future<out T?>> = willReturn(equalTo(expected))

/**
 * Matches a Flow that succeeds with a result matched by the given matcher
 */
fun <T> willReturn(successMatcher: Matcher<T>) = object : Matcher<Future<out T>> {
    override val description: String = "is a future that will succeed with a value that ${successMatcher.description}"

    override fun invoke(actual: Future<out T>): MatchResult = try {
        successMatcher(actual.getOrThrow()).modifyMismatchDescription { "succeeded with value that $it" }
    } catch (e: Exception) {
        MatchResult.Mismatch("failed with $e")
    }
}

/**
 * Matches a Flow that fails, with an exception matched by the given matcher.
 */
inline fun <reified E: Exception> willThrow(failureMatcher: Matcher<E>) = object : Matcher<Future<*>> {
    override val description: String
        get() = "is a future that will fail with a ${E::class.java.simpleName} that ${failureMatcher.description}"

    override fun invoke(actual: Future<*>): MatchResult = try {
        actual.getOrThrow()
        MatchResult.Mismatch("succeeded")
    } catch (e: Exception) {
        when(e) {
            is E -> failureMatcher(e).modifyMismatchDescription { "failed with ${E::class.java.simpleName} that $it" }
            else -> MatchResult.Mismatch("failed with ${e.javaClass}")
        }
    }
}

/**
 * Matches a Flow that fails, with an exception of the specified type.
 */
inline fun <reified E: Exception> willThrow() = object : Matcher<Future<*>> {
    override val description: String
        get() = "is a future that will fail with a ${E::class.java}"

    override fun invoke(actual: Future<*>): MatchResult = try {
        actual.getOrThrow()
        MatchResult.Mismatch("succeeded")
    } catch (e: Exception) {
        when(e) {
            is E -> MatchResult.Match
            else -> MatchResult.Mismatch("failed with ${e.javaClass}")
        }
    }
}
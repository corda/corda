package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * This class provides simple tracking of the lifecycle of a service-type object.
 * [S] is an enum enumerating the possible states the service can be in.
 *
 * @param initial The initial state.
 */
@DeleteForDJVM
class LifeCycle<S : Enum<S>>(initial: S) {
    private val lock = ReentrantReadWriteLock()
    private var state = initial

    /**
     * Assert that the lifecycle in the [requiredState]. Optionally runs [block], for the duration of which the
     * lifecycle is guaranteed to stay in [requiredState].
     */
    fun <A> requireState(
            requiredState: S,
            block: () -> A
    ): A {
        return requireState(
                errorMessage = { "Required state to be $requiredState, was $it" },
                predicate = { it == requiredState },
                block = block
        )
    }
    fun requireState(requiredState: S) = requireState(requiredState) {}

    fun <A> requireState(
            requiredState: S,
            throwable: Throwable,
            block: () -> A
    ): A {
        return lock.readLock().withLock {
           if (requiredState != state) { throw throwable }
            block()
        }
    }

    /** Assert something about the current state atomically. */
    fun <A> requireState(
            errorMessage: (S) -> String,
            predicate: (S) -> Boolean,
            block: () -> A
    ): A {
        return lock.readLock().withLock {
            require(predicate(state)) { errorMessage(state) }
            block()
        }
    }
    fun requireState(
            errorMessage: (S) -> String = { "Predicate failed on state $it" },
            predicate: (S) -> Boolean
    ) {
        requireState(errorMessage, predicate) {}
    }

    /** Transition the state from [from] to [to]. */
    fun transition(from: S, to: S) {
        lock.writeLock().withLock {
            require(state == from) { "Required state to be $from to transition to $to, was $state" }
            state = to
        }
    }

    /** Transition the state to [to] without performing a current state check. */
    fun justTransition(to: S) {
        lock.writeLock().withLock {
            state = to
        }
    }
}
@file:Deterministic
package net.corda.core.utilities

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.Deterministic
import net.corda.core.flows.FlowException
import net.corda.core.internal.castIfPossible
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import java.io.Serializable

/**
 * A small utility to approximate taint tracking: if a method gives you back one of these, it means the data came from
 * a remote source that may be incentivised to pass us junk that violates basic assumptions and thus must be checked
 * first. The wrapper helps you to avoid forgetting this vital step. Things you might want to check are:
 *
 * - Is this object the one you actually expected? Did the other side hand you back something technically valid but
 *   not what you asked for?
 * - Is the object disobeying its own invariants?
 * - Are any objects *reachable* from this object mismatched or not what you expected?
 * - Is it suspiciously large or small?
 */
@Deterministic
class UntrustworthyData<out T>(@PublishedApi internal val fromUntrustedWorld: T) {
    @Suspendable
    @Throws(FlowException::class)
    fun <R> unwrap(validator: Validator<T, R>) = validator.validate(fromUntrustedWorld)

    @Deterministic
    @FunctionalInterface
    interface Validator<in T, out R> : Serializable {
        @Suspendable
        @Throws(FlowException::class)
        fun validate(data: T): R
    }
}

inline fun <T, R> UntrustworthyData<T>.unwrap(validator: (T) -> R): R = validator(fromUntrustedWorld)

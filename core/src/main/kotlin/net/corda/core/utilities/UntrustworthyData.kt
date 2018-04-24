/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.utilities

import co.paralleluniverse.fibers.Suspendable
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
class UntrustworthyData<out T>(@PublishedApi internal val fromUntrustedWorld: T) {
    @Suspendable
    @Throws(FlowException::class)
    fun <R> unwrap(validator: Validator<T, R>) = validator.validate(fromUntrustedWorld)

    @FunctionalInterface
    interface Validator<in T, out R> : Serializable {
        @Suspendable
        @Throws(FlowException::class)
        fun validate(data: T): R
    }
}

inline fun <T, R> UntrustworthyData<T>.unwrap(validator: (T) -> R): R = validator(fromUntrustedWorld)

fun <T : Any> SerializedBytes<Any>.checkPayloadIs(type: Class<T>): UntrustworthyData<T> {
    val payloadData: T = try {
        val serializer = SerializationDefaults.SERIALIZATION_FACTORY
        serializer.deserialize(this, type, SerializationDefaults.P2P_CONTEXT)
    } catch (ex: Exception) {
        throw IllegalArgumentException("Payload invalid", ex)
    }
    return type.castIfPossible(payloadData)?.let { UntrustworthyData(it) } ?:
            throw IllegalArgumentException("We were expecting a ${type.name} but we instead got a " +
                    "${payloadData.javaClass.name} (${payloadData})")
}

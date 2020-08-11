package net.corda.core.flows

import net.corda.core.CordaRuntimeException
import net.corda.core.serialization.internal.MissingSerializerException

/**
 * Thrown whenever a flow result cannot be serialized when attempting to save it in the database
 */
class ResultSerializationException private constructor(message: String?) : CordaRuntimeException(message) {
    constructor(e: MissingSerializerException): this(e.message)
}
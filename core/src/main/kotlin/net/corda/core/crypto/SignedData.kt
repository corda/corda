package net.corda.core.crypto

import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import java.security.SignatureException

/**
 * A serialized piece of data and its signature. Enforces signature validity in order to deserialize the data
 * contained within.
 *
 * @param raw the raw serialized data.
 * @param sig the (unverified) signature for the data.
 */
@CordaSerializable
open class SignedData<T : Any>(val raw: SerializedBytes<T>, val sig: DigitalSignature.WithKey) {
    /**
     * Return the deserialized data if the signature can be verified.
     *
     * @throws IllegalArgumentException if the data is invalid (only used if verifyData() is overloaded).
     * @throws SignatureException if the signature is invalid.
     */
    @Throws(SignatureException::class)
    fun verified(): T {
        sig.verify(raw)
        val data: T = uncheckedCast(raw.deserialize<Any>())
        verifyData(data)
        return data
    }

    /**
     * Verify the wrapped data after the signature has been verified and the data deserialised. Provided as an extension
     * point for subclasses.
     *
     * @throws IllegalArgumentException if the data is invalid.
     */
    @Throws(IllegalArgumentException::class)
    protected open fun verifyData(data: T) {
        // By default we accept anything
    }
}

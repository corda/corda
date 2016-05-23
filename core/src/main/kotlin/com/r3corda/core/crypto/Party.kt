package com.r3corda.core.crypto

import com.r3corda.core.contracts.PartyAndReference
import com.r3corda.core.serialization.OpaqueBytes
import java.security.PublicKey

/**
 * Created by matth on 14/05/2016.
 */
/** A [Party] is well known (name, pubkey) pair. In a real system this would probably be an X.509 certificate. */
data class Party(val name: String, val owningKey: PublicKey) {
    override fun toString() = name

    fun ref(bytes: OpaqueBytes) = PartyAndReference(this, bytes)
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}
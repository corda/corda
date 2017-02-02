package net.corda.core.crypto

import net.corda.core.contracts.PartyAndReference
import net.corda.core.serialization.OpaqueBytes
import java.security.PublicKey

/**
 * The [AnonymousParty] class contains enough information to uniquely identify a [Party] while excluding private
 * information such as name. It is intended to represent a party on the distributed ledger.
 */
open class AnonymousParty(val owningKey: CompositeKey) {
    /** A helper constructor that converts the given [PublicKey] in to a [CompositeKey] with a single node */
    constructor(owningKey: PublicKey) : this(owningKey.composite)

    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other is AnonymousParty && this.owningKey == other.owningKey
    override fun hashCode(): Int = owningKey.hashCode()
    // Use the key as the bulk of the toString(), but include a human readable identifier as well, so that [Party]
    // can put in the key and actual name
    override fun toString() = "${owningKey.toBase58String()} <Anonymous>"

    fun ref(bytes: OpaqueBytes) = PartyAndReference(this, bytes)
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}
package net.corda.core.identity

import net.corda.core.Deterministic
import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.toStringShort
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey

/**
 * The [AnonymousParty] class contains enough information to uniquely identify a [Party] while excluding private
 * information such as name. It is intended to represent a party on the distributed ledger.
 */
@Deterministic
class AnonymousParty(owningKey: PublicKey) : AbstractParty(owningKey) {
    override fun nameOrNull(): CordaX500Name? = null
    override fun ref(bytes: OpaqueBytes): PartyAndReference = PartyAndReference(this, bytes)
    override fun toString() = "Anonymous(${owningKey.toStringShort()})"
}
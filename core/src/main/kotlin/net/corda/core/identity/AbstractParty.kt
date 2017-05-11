package net.corda.core.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.OpaqueBytes
import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey

/**
 * An [AbstractParty] contains the common elements of [Party] and [AnonymousParty], specifically the owning key of
 * the party. In most cases [Party] or [AnonymousParty] should be used, depending on use-case.
 *
 * We don't override equals/hashCode here as [Party] and [AnonymousParty] are intentionally never equal. Equality on
 * [Party] means "These are the same party", on [AnonymousParty] it means "These have the same key", which are very
 * different tests. In the unlikely case that the two need to be tested, resolve the anonymous party to the full
 * party first if possible, or convert the full party to an anonymous party otherwise. Think very carefully about intent
 * before doing so, though.
 */
@CordaSerializable
abstract class AbstractParty(val owningKey: PublicKey) {
    abstract fun toAnonymous(): AnonymousParty
    abstract val nameOrNull: X500Name?

    abstract fun ref(bytes: OpaqueBytes): PartyAndReference
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}
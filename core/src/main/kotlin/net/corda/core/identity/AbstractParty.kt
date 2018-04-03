package net.corda.core.identity

import net.corda.annotations.serialization.Serializable
import net.corda.core.DoNotImplement
import net.corda.core.contracts.PartyAndReference
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey

/**
 * An [AbstractParty] contains the common elements of [Party] and [AnonymousParty], specifically the owning key of
 * the party. In most cases [Party] or [AnonymousParty] should be used, depending on use-case.
 */
@Serializable
@DoNotImplement
abstract class AbstractParty(val owningKey: PublicKey) {
    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other === this || other is AbstractParty && other.owningKey == owningKey

    override fun hashCode(): Int = owningKey.hashCode()
    abstract fun nameOrNull(): CordaX500Name?

    /**
     * Build a reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
     * ledger.
     */
    abstract fun ref(bytes: OpaqueBytes): PartyAndReference

    /**
     * Build a reference to something being stored or issued by a party e.g. in a vault or (more likely) on their normal
     * ledger.
     */
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}
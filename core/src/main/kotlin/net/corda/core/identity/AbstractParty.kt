package net.corda.core.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.OpaqueBytes
import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey

/**
 * An [AbstractParty] contains the common elements of [Party] and [AnonymousParty], specifically the owning key of
 * the party. In most cases [Party] or [AnonymousParty] should be used, depending on use-case.
 */
@CordaSerializable
abstract class AbstractParty(val owningKey: PublicKey) {
    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other is AbstractParty && this.owningKey == other.owningKey

    override fun hashCode(): Int = owningKey.hashCode()
    abstract fun nameOrNull(): X500Name?

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
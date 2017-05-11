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
    // TODO: Should [AnonymousParty] and [Party] ever be considered equal, or should this be false if they're not the
    //       same type?
    override fun equals(other: Any?): Boolean {
        return if (other is AbstractParty) {
            if (this.nameOrNull != null && other.nameOrNull != null)
                this.nameOrNull == other.nameOrNull
            else
                this.owningKey == other.owningKey
        } else {
            false
        }
    }

    override fun hashCode(): Int = nameOrNull?.hashCode() ?: owningKey.hashCode()

    abstract fun toAnonymous(): AnonymousParty
    abstract val nameOrNull: X500Name?

    abstract fun ref(bytes: OpaqueBytes): PartyAndReference
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}
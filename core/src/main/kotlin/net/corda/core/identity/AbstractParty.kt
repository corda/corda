package net.corda.core.identity

import net.corda.core.CordaInternal
import net.corda.core.DoNotImplement
import net.corda.core.contracts.PartyAndReference
import net.corda.core.flows.Destination
import net.corda.core.internal.utilities.Internable
import net.corda.core.internal.utilities.IternabilityVerifier
import net.corda.core.internal.utilities.PrivateInterner
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey

/**
 * An [AbstractParty] contains the common elements of [Party] and [AnonymousParty], specifically the owning key of
 * the party. In most cases [Party] or [AnonymousParty] should be used, depending on use-case.
 */
@CordaSerializable
@DoNotImplement
abstract class AbstractParty(val owningKey: PublicKey): Destination {
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

    @CordaInternal
    companion object : Internable<AbstractParty> {
        @CordaInternal
        override val interner = PrivateInterner<AbstractParty>(object : IternabilityVerifier<AbstractParty> {
            override fun choose(original: AbstractParty, interned: AbstractParty): AbstractParty {
                // Because Party does not compare name in equals(), don't intern if there's a clash
                return if (original.nameOrNull() != interned.nameOrNull()) original else interned
            }
        })
    }
}
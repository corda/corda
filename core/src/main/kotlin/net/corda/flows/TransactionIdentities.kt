package net.corda.flows

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class TransactionIdentities(val identities: List<Pair<Party, AnonymisedIdentity>>) {
    constructor(vararg identities: Pair<Party, AnonymisedIdentity>) : this(identities.toList())

    init {
        require(identities.size == identities.map { it.first }.toSet().size) { "Identities must be unique: ${identities.map { it.first }}" }
    }

    fun forParty(party: Party): AnonymisedIdentity = identities.single { it.first == party }.second
    fun toMap(): Map<Party, AnonymisedIdentity> = this.identities.toMap()
}
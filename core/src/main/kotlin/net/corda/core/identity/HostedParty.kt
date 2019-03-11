package net.corda.core.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey

@CordaSerializable
class HostedParty(val host: Party, owningKey: PublicKey) : AbstractParty(owningKey) {

    override fun nameOrNull(): CordaX500Name? {
        return host.name;
    }

    override fun ref(bytes: OpaqueBytes): PartyAndReference {
        TODO("not implemented")
    }

    override fun host(): AbstractParty {
        return host
    }
}
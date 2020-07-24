package net.corda.core.flows

import net.corda.core.CordaRuntimeException
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

@CordaSerializable
data class PartyIdentity(
        val partyKey: PublicKey? = null,
        val partyName: CordaX500Name? = null
)

class PartyNotFoundException(
        message: String,
        val party: PartyIdentity
) : CordaRuntimeException(message)
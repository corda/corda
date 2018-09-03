package net.corda.node.services.transactions

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.core.node.ServiceHub
import java.security.PublicKey

/** A validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftValidatingNotaryService(
        override val services: ServiceHub,
        override val notaryIdentityKey: PublicKey,
        override val uniquenessProvider: RaftUniquenessProvider
) : TrustedAuthorityNotaryService() {
    override fun createServiceFlow(otherPartySession: FlowSession): NotaryServiceFlow {
        return ValidatingNotaryFlow(otherPartySession, this)
    }

    override fun start() {
        uniquenessProvider.start()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}

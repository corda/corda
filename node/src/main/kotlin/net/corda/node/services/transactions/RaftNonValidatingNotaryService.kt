package net.corda.node.services.transactions

import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotaryFlow
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.TrustedAuthorityNotaryService
import java.security.PublicKey

/** A non-validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftNonValidatingNotaryService(
        override val services: ServiceHub,
        override val notaryIdentityKey: PublicKey,
        override val uniquenessProvider: RaftUniquenessProvider
) : TrustedAuthorityNotaryService() {
    override val timeWindowChecker: TimeWindowChecker = TimeWindowChecker(services.clock)
    
    override fun createServiceFlow(otherPartySession: FlowSession): NotaryFlow.Service {
        return NonValidatingNotaryFlow(otherPartySession, this)
    }

    override fun start() {
        uniquenessProvider.start()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}
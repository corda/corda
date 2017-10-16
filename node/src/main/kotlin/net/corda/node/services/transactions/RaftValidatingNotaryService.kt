package net.corda.node.services.transactions

import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotaryFlow
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.RaftConfig
import java.security.PublicKey

/** A validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftValidatingNotaryService(override val services: ServiceHubInternal,
                                  override val notaryIdentityKey: PublicKey,
                                  raftConfig: RaftConfig) : TrustedAuthorityNotaryService() {
    companion object {
        val id = constructId(validating = true, raft = true)
    }

    override val timeWindowChecker: TimeWindowChecker = TimeWindowChecker(services.clock)
    override val uniquenessProvider: RaftUniquenessProvider = RaftUniquenessProvider(services, raftConfig)

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryFlow.Service {
        return ValidatingNotaryFlow(otherPartySession, this)
    }

    override fun start() {
        uniquenessProvider.start()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}

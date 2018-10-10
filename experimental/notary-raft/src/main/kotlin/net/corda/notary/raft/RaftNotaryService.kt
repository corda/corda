package net.corda.notary.raft

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.node.services.transactions.ValidatingNotaryFlow
import java.security.PublicKey

/** A highly available notary service using the Raft algorithm to achieve consensus. */
class RaftNotaryService(
        override val services: ServiceHubInternal,
        override val notaryIdentityKey: PublicKey
) : TrustedAuthorityNotaryService() {
    private val notaryConfig = services.configuration.notary
            ?: throw IllegalArgumentException("Failed to register ${this::class.java}: notary configuration not present")

    override val uniquenessProvider = with(services) {
        val raftConfig = notaryConfig.raft
                ?: throw IllegalArgumentException("Failed to register ${this::class.java}: raft configuration not present")
        RaftUniquenessProvider(
                configuration.baseDirectory,
                configuration.p2pSslOptions,
                database,
                clock,
                monitoringService.metrics,
                services.cacheFactory,
                raftConfig
        )
    }

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryServiceFlow {
        return if (notaryConfig.validating) {
            ValidatingNotaryFlow(otherPartySession, this)
        } else NonValidatingNotaryFlow(otherPartySession, this)
    }

    override fun start() {
        uniquenessProvider.start()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}

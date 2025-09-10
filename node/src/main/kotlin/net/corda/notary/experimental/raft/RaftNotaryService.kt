package net.corda.notary.experimental.raft

import net.corda.node.services.api.ServiceHubInternal
import net.corda.notary.common.ValidationModeNotaryService
import java.security.PublicKey

/** A highly available notary service using the Raft algorithm to achieve consensus. */
class RaftNotaryService(services: ServiceHubInternal, notaryIdentityKey: PublicKey) : ValidationModeNotaryService(services, notaryIdentityKey) {
    override val uniquenessProvider = with(services) {
        val raftConfig = notaryConfig.raft
                ?: throw IllegalArgumentException("Failed to register ${RaftNotaryService::class.java}: raft configuration not present")

        RaftUniquenessProvider(
                configuration.baseDirectory,
                configuration.p2pSslOptions,
                database,
                clock,
                monitoringService.metrics,
                services.cacheFactory,
                raftConfig,
                ::signTransaction
        )
    }

    override fun start() {
        uniquenessProvider.start()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}

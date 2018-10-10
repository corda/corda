package net.corda.notary.mysql

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.AsyncCFTNotaryService
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.node.services.transactions.ValidatingNotaryFlow
import java.security.PublicKey

/** Notary service backed by a replicated MySQL database. */
class MySQLNotaryService(
        override val services: ServiceHubInternal,
        override val notaryIdentityKey: PublicKey) : AsyncCFTNotaryService() {

    /** Database table will be automatically created in dev mode */
    private val devMode = services.configuration.devMode

    private val notaryConfig = services.configuration.notary
            ?: throw IllegalArgumentException("Failed to register ${this::class.java}: notary configuration not present")

    override val asyncUniquenessProvider = with(services) {
        val mysqlConfig = notaryConfig.mysql
                ?: throw IllegalArgumentException("Failed to register ${this::class.java}: raft configuration not present")
        MySQLUniquenessProvider(
                services.monitoringService.metrics,
                services.clock,
                mysqlConfig
        )
    }

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryServiceFlow {
        return if (notaryConfig.validating) {
            ValidatingNotaryFlow(otherPartySession, this)
        } else NonValidatingNotaryFlow(otherPartySession, this)
    }


    override fun start() {
        if (devMode) asyncUniquenessProvider.createTable()
    }

    override fun stop() {
        asyncUniquenessProvider.stop()
    }
}
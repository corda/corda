package net.corda.notary.mysql

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.node.services.transactions.ValidatingNotaryFlow
import net.corda.nodeapi.internal.config.parseAs
import java.security.PublicKey

/** Notary service backed by a replicated MySQL database. */
class MySQLNotaryService(
        override val services: ServiceHubInternal,
        override val notaryIdentityKey: PublicKey) : SinglePartyNotaryService() {

    /** Database table will be automatically created in dev mode */
    private val devMode = services.configuration.devMode

    private val notaryConfig = services.configuration.notary
            ?: throw IllegalArgumentException("Failed to register ${this::class.java}: notary configuration not present")

    override val uniquenessProvider = with(services) {
        val mysqlConfig = try {
            notaryConfig.extraConfig!!.parseAs<MySQLNotaryConfiguration>()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to register ${MySQLNotaryService::class.java}: mysql configuration not present")
        }
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
        if (devMode) uniquenessProvider.createTable()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}
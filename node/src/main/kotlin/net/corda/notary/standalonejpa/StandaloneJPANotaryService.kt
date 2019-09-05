package net.corda.notary.standalonejpa

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.seconds
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.node.services.transactions.ValidatingNotaryFlow
import java.security.PublicKey

/** Notary service backed by Hibernate, an implementation of the JPA standard. */
class StandaloneJPANotaryService(
        override val services: ServiceHubInternal,
        override val notaryIdentityKey: PublicKey) : SinglePartyNotaryService() {

    private val notaryConfig = services.configuration.notary
            ?: throw IllegalArgumentException("Failed to register ${this::class.java}: notary configuration not present")

    override val uniquenessProvider = StandaloneJPAUniquenessProvider(services.monitoringService.metrics, services.clock, services.configuration.notary!!.jpa!!)

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryServiceFlow {
        return if (notaryConfig.validating) {
            ValidatingNotaryFlow(otherPartySession, this, notaryConfig.etaMessageThresholdSeconds.seconds)
        } else NonValidatingNotaryFlow(otherPartySession, this, notaryConfig.etaMessageThresholdSeconds.seconds)
    }

    override fun start() {
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}
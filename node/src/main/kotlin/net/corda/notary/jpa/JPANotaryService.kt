package net.corda.notary.jpa

import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.utilities.seconds
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.node.services.transactions.ValidatingNotaryFlow
import net.corda.nodeapi.internal.config.parseAs
import net.corda.notary.common.signBatch

/** Notary service backed by a relational database. */
class JPANotaryService(
        override val services: ServiceHubInternal,
        override val notaryIdentity: Party) : SinglePartyNotaryService() {

    private val notaryConfig = services.configuration.notary
            ?: throw IllegalArgumentException("Failed to register ${this::class.java}: notary configuration not present")


    @Suppress("TooGenericExceptionCaught")
    override val uniquenessProvider = with(services) {
        val jpaNotaryConfig = try {
            notaryConfig.extraConfig?.parseAs() ?: JPANotaryConfiguration()
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to register ${JPANotaryService::class.java}: extra notary configuration parameters invalid")
        }
        JPAUniquenessProvider(
                clock,
                database,
                jpaNotaryConfig,
                configuration.myLegalName,
                ::signTransactionBatch
        )
    }

    private fun signTransactionBatch(txIds: Iterable<SecureHash>)
            = signBatch(txIds, notaryIdentityKey, services)

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

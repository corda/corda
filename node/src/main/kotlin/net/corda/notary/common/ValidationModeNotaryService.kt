package net.corda.notary.common

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.utilities.seconds
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.node.services.transactions.ValidatingNotaryFlow
import java.security.PublicKey

abstract class ValidationModeNotaryService(override val services: ServiceHubInternal,
                                           override val notaryIdentityKey: PublicKey) : SinglePartyNotaryService() {

    val notaryConfig: NotaryConfig = requireNotNull(services.configuration.notary) {
        "Failed to register ${javaClass.name}: notary configuration not present"
    }

    final override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> {
        return if (notaryConfig.validating) {
            ValidatingNotaryFlow(otherPartySession, this, notaryConfig.etaMessageThresholdSeconds.seconds)
        } else {
            NonValidatingNotaryFlow(otherPartySession, this, notaryConfig.etaMessageThresholdSeconds.seconds)
        }
    }
}

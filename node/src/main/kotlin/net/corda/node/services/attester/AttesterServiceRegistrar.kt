package net.corda.node.services.attester

import net.corda.core.flows.BackchainAttesterClientFlow
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.AttesterServiceType
import net.corda.node.internal.FlowManager
import net.corda.node.services.config.AttesterConfiguration
import java.security.PublicKey

object AttesterServiceRegistrar {
    fun register(config: AttesterConfiguration, flowManager: FlowManager, attesterKey: PublicKey) {
        require(config.type == AttesterServiceType.BACKCHAIN_VALIDATOR)
        val flowFactory = { session: FlowSession ->
            val attesterService = MockBackChainAttester()
            AttesterServiceFlow(session, attesterService, attesterKey)
        }
        flowManager.registerInitiatedCoreFlowFactory(BackchainAttesterClientFlow::class, flowFactory)
    }
}
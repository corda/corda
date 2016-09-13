package com.r3corda.node.services.clientapi

import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.TwoPartyDealProtocol
import com.r3corda.protocols.TwoPartyDealProtocol.FIX_INITIATE_TOPIC
import com.r3corda.protocols.TwoPartyDealProtocol.FixingSessionInitiation

/**
 * This is a temporary handler required for establishing random sessionIDs for the [Fixer] and [Floater] as part of
 * running scheduled fixings for the [InterestRateSwap] contract.
 *
 * TODO: This will be replaced with the automatic sessionID / session setup work.
 */
object FixingSessionInitiation {
    class Plugin: CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    class Service(services: ServiceHubInternal) : AbstractNodeService(services) {
        init {
            addProtocolHandler(FIX_INITIATE_TOPIC, "fixings") { initiation: FixingSessionInitiation ->
                TwoPartyDealProtocol.Fixer(initiation.replyToParty, initiation.oracleType)
            }
        }
    }
}

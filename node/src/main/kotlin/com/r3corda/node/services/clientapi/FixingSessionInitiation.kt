package com.r3corda.node.services.clientapi

import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.serialization.deserialize
import com.r3corda.node.internal.AbstractNode
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.TwoPartyDealProtocol

/**
 * This is a temporary handler required for establishing random sessionIDs for the [Fixer] and [Floater] as part of
 * running scheduled fixings for the [InterestRateSwap] contract.
 *
 * TODO: This will be replaced with the automatic sessionID / session setup work.
 */
object FixingSessionInitiation {
    class Plugin: CordaPluginRegistry {
        override val webApis: List<Class<*>> = emptyList()
        override val requiredProtocols: Map<String, Set<String>> = emptyMap()
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    class Service(services: ServiceHubInternal) {
        init {
            services.networkService.addMessageHandler("${TwoPartyDealProtocol.FIX_INITIATE_TOPIC}.0") { msg, registration ->
                val initiation = msg.data.deserialize<TwoPartyDealProtocol.FixingSessionInitiation>()
                val protocol = TwoPartyDealProtocol.Fixer(initiation)
                services.startProtocol("fixings", protocol)
            }
        }
    }
}

package com.r3corda.node.services.clientapi

import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.protocols.TwoPartyDealProtocol.Fixer
import com.r3corda.protocols.TwoPartyDealProtocol.Floater

/**
 * This is a temporary handler required for establishing random sessionIDs for the [Fixer] and [Floater] as part of
 * running scheduled fixings for the [InterestRateSwap] contract.
 *
 * TODO: This will be replaced with the symmetric session work
 */
object FixingSessionInitiation {
    class Plugin: CordaPluginRegistry() {
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    class Service(services: ServiceHubInternal) : SingletonSerializeAsToken() {
        init {
            services.registerProtocolInitiator(Floater::class) { Fixer(it) }
        }
    }
}

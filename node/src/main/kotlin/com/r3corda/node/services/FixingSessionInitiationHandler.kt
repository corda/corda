package com.r3corda.node.services

import com.r3corda.core.serialization.deserialize
import com.r3corda.node.internal.AbstractNode
import com.r3corda.protocols.TwoPartyDealProtocol

/**
 * This is a temporary handler required for establishing random sessionIDs for the [Fixer] and [Floater] as part of
 * running scheduled fixings for the [InterestRateSwap] contract.
 *
 * TODO: This will be replaced with the automatic sessionID / session setup work.
 */
object FixingSessionInitiationHandler {

    fun register(node: AbstractNode) {
        node.net.addMessageHandler("${TwoPartyDealProtocol.FIX_INITIATE_TOPIC}.0") { msg, registration ->
            val initiation = msg.data.deserialize<TwoPartyDealProtocol.FixingSessionInitiation>()
            val protocol = TwoPartyDealProtocol.Fixer(initiation)
            node.smm.add("fixings", protocol)
        }
    }
}

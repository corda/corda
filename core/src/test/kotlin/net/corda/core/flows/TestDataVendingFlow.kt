package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.node.flows.FetchDataFlow
import net.corda.core.node.flows.SendStateAndRefFlow
import net.corda.core.utilities.UntrustworthyData

// Flow to start data vending without sending transaction. For testing only.
class TestDataVendingFlow(otherSide: Party) : SendStateAndRefFlow(otherSide, emptyList()) {
    @Suspendable
    override fun sendPayloadAndReceiveDataRequest(otherSide: Party, payload: Any): UntrustworthyData<FetchDataFlow.Request> {
        return if (payload is List<*> && payload.isEmpty()) {
            // Hack to not send the first message.
            receive(otherSide)
        } else {
            super.sendPayloadAndReceiveDataRequest(otherSide, payload)
        }
    }
}
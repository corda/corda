package net.corda.testing

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.SendStateAndRefFlow
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.utilities.UntrustworthyData

// Flow to start data vending without sending transaction. Copied from [SendTransactionFlow], for testing only.
class DataVendingFlow(otherSide: Party) : SendStateAndRefFlow(otherSide, emptyList()) {
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
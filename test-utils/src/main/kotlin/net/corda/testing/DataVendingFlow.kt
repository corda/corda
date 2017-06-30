package net.corda.testing

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.flows.TransactionData
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.utilities.UntrustworthyData

// Flow to start data vending without sending transaction.
class DataVendingFlow(otherSide: Party) : SendTransactionFlow(otherSide, emptySet()) {
    @Suspendable
    override fun sendPayloadAndReceiveDataRequest(otherSide: Party, payload: Any): UntrustworthyData<FetchDataFlow.Request> {
        // Hacks to do receive only for unit test.
        return if (payload is TransactionData.TransactionHashesData && payload.tx.isEmpty()) {
            receive<FetchDataFlow.Request>(otherSide)
        } else {
            super.sendPayloadAndReceiveDataRequest(otherSide, payload)
        }
    }
}
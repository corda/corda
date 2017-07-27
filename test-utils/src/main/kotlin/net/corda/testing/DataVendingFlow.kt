package net.corda.testing

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.utilities.unwrap

// Flow to start data vending without sending transaction. Copied from [SendTransactionFlow], for testing only.
class DataVendingFlow(val otherSide: Party) : FlowLogic<Unit>() {
    @Suspendable
    private fun sendPayloadAndReceiveDataRequest(otherSide: Party, payload: Any?) = payload?.let { sendAndReceive<FetchDataFlow.Request>(otherSide, payload) } ?: receive<FetchDataFlow.Request>(otherSide)

    @Suspendable
    override fun call() {
        // The first payload will be the transaction data, subsequent payload will be the transaction/attachment data.
        var payload: Any? = null
        // This loop will receive [FetchDataFlow.Request] continuously until the `otherSide` has all the data they need
        // to resolve the transaction, a [FetchDataFlow.EndRequest] will be sent from the `otherSide` to indicate end of
        // data request.
        while (true) {
            val dataRequest = sendPayloadAndReceiveDataRequest(otherSide, payload).unwrap { request ->
                when (request) {
                    is FetchDataFlow.Request.Data -> request
                    FetchDataFlow.Request.End -> return
                }
            }
            payload = when (dataRequest.dataType) {
                FetchDataFlow.DataType.TRANSACTION -> dataRequest.hashes.map {
                    serviceHub.validatedTransactions.getTransaction(it) ?: throw FetchDataFlow.HashNotFound(it)
                }
                FetchDataFlow.DataType.ATTACHMENT -> dataRequest.hashes.map {
                    serviceHub.attachments.openAttachment(it)?.open()?.readBytes() ?: throw FetchDataFlow.HashNotFound(it)
                }
            }
        }
    }
}
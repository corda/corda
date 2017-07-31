package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * The [SendTransactionFlow] should be used to send a transaction to another peer that wishes to verify that transaction's
 * integrity by resolving and checking the dependencies as well. The other side should invoke [ReceiveTransactionFlow] at
 * the right point in the conversation to receive the sent transaction and perform the resolution back-and-forth required
 * to check the dependencies and download any missing attachments.
 *
 * @param otherSide the target party.
 * @param stx the [SignedTransaction] being sent to the [otherSide].
 */
open class SendTransactionFlow(otherSide: Party, stx: SignedTransaction) : DataVendingFlow<SignedTransaction>(otherSide, stx)

/**
 * The [SendStateAndRefFlow] should be used to send a list of input [StateAndRef] to another peer that wishes to verify
 * the input's integrity by resolving and checking the dependencies as well. The other side should invoke [ReceiveStateAndRefFlow]
 * at the right point in the conversation to receive the input state and ref and perform the resolution back-and-forth
 * required to check the dependencies.
 *
 * @param otherSide the target party.
 * @param stateAndRefs the list of [StateAndRef] being sent to the [otherSide].
 */
open class SendStateAndRefFlow(otherSide: Party, stateAndRefs: List<StateAndRef<*>>) : DataVendingFlow<List<StateAndRef<*>>>(otherSide, stateAndRefs)

sealed class DataVendingFlow<out T : Any>(val otherSide: Party, val payload: T) : FlowLogic<Unit>() {
    @Suspendable
    protected open fun sendPayloadAndReceiveDataRequest(otherSide: Party, payload: Any) = sendAndReceive<FetchDataFlow.Request>(otherSide, payload)

    @Suspendable
    protected open fun verifyDataRequest(dataRequest: FetchDataFlow.Request.Data) {
        // Security TODO: Check for abnormally large or malformed data requests
    }

    @Suspendable
    override fun call() {
        // The first payload will be the transaction data, subsequent payload will be the transaction/attachment data.
        var payload: Any = payload
        // This loop will receive [FetchDataFlow.Request] continuously until the `otherSide` has all the data they need
        // to resolve the transaction, a [FetchDataFlow.EndRequest] will be sent from the `otherSide` to indicate end of
        // data request.
        while (true) {
            val dataRequest = sendPayloadAndReceiveDataRequest(otherSide, payload).unwrap { request ->
                when (request) {
                    is FetchDataFlow.Request.Data -> {
                        verifyDataRequest(request)
                        request
                    }
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

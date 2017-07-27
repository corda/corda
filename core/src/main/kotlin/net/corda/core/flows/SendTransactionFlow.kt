package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * The [SendTransactionFlow] should be called in response to the [ReceiveTransactionFlow]. This flow sends the
 * [SignedTransaction] to the [otherSide].
 *
 * After sending the [SignedTransaction], the [SendTransactionFlow] will listen for incoming [FetchDataFlow.Request]
 * from the [otherSide], the requested data will be used for the transaction resolving process.
 *
 * The number of request from [ReceiveTransactionFlow] depends on the depth of the transaction history and the data
 * [otherSide] already possess. The [SendTransactionFlow] is expected to receive [FetchDataFlow.Request] continuously
 * until the [otherSide] has all the data they need to resolve the transaction, an [FetchDataFlow.Request.End] will be
 * sent from the [otherSide] to indicate end of data request.
 *
 * @param otherSide the target party.
 * @param stx the [SignedTransaction] being sent to the [otherSide].
 */
class SendTransactionFlow(otherSide: Party, stx: SignedTransaction) : DataVendingFlow<SignedTransaction>(otherSide, stx)

/**
 * The [SendProposalFlow] should be called in response to the [ReceiveProposalFlow]. This flow sends the [TradeProposal]
 * to the [otherSide].
 *
 * After sending the [TradeProposal], the [SendProposalFlow] will listen for incoming [FetchDataFlow.Request]
 * from the [otherSide], the requested data will be used for the transaction resolving process.
 *
 * The number of request from [ReceiveProposalFlow] depends on the depth of the transaction history and the data
 * [otherSide] already possess. The [SendProposalFlow] is expected to receive [FetchDataFlow.Request] continuously
 * until the [otherSide] has all the data they need to resolve the transaction, an [FetchDataFlow.Request.End] will be
 * sent from the [otherSide] to indicate end of data request.
 *
 * @param otherSide the target party.
 * @param tradeProposal the [TradeProposal] being sent to the [otherSide].
 */
class SendProposalFlow(otherSide: Party, tradeProposal: TradeProposal<*>) : DataVendingFlow<TradeProposal<*>>(otherSide, tradeProposal)

open class DataVendingFlow<out T : Any>(val otherSide: Party, val payload: T) : FlowLogic<Unit>() {
    @Suspendable
    protected open fun sendPayloadAndReceiveDataRequest(otherSide: Party, payload: Any) = sendAndReceive<FetchDataFlow.Request>(otherSide, payload)

    @Suspendable
    protected open fun verifyDataRequest(dataRequest: FetchDataFlow.Request.Data) {
        // Security TODO: Make sure request is relevant to the transaction.
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

/**
 * TODO: API DOCs
 */
interface TradeProposal<out T : ContractState> {
    val inputStates: List<StateAndRef<T>>
}

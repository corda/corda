package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.TransactionData.SignedTransactionData
import net.corda.core.flows.TransactionData.TransactionHashesData
import net.corda.core.identity.Party
import net.corda.core.internal.FetchDataFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * The [SendTransactionFlow] corresponds to the [ReceiveTransactionFlow].
 *
 * The [SendTransactionFlow] provides an ad hoc data vending service, which anticipates incoming data request from the
 * [otherSide] during the transaction resolving process.
 *
 * The number of request from [ReceiveTransactionFlow] is depends on the depth of the transaction history and the data
 * [otherSide] already possess. The [SendTransactionFlow] is expected to receive [FetchDataFlow.Request] continuously
 * until the [otherSide] has all the data they need to resolve the transaction, an [FetchDataFlow.Request.End] will be
 * sent from the [otherSide] to indicate end of data request.
 *
 * @param otherSide the target party.
 * @param payload the message that will be sent to the [otherSide] before data vending starts.
 */

open class SendTransactionFlow private constructor(val otherSide: Party, val payload: TransactionData<*>) : FlowLogic<Unit>() {
    @JvmOverloads
    constructor(otherSide: Party, stx: SignedTransaction, extraData: Any? = null) : this(otherSide, SignedTransactionData(stx, extraData))

    @JvmOverloads
    constructor(otherSide: Party, hashes: Set<SecureHash>, extraData: Any? = null) : this(otherSide, TransactionHashesData(hashes, extraData))

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

@CordaSerializable
sealed class TransactionData<out T : Any>(val tx: T, val extraData: Any?) {
    class SignedTransactionData(stx: SignedTransaction, extraData: Any?) : TransactionData<SignedTransaction>(stx, extraData)
    class TransactionHashesData(hashes: Set<SecureHash>, extraData: Any?) : TransactionData<Set<SecureHash>>(hashes, extraData)
}

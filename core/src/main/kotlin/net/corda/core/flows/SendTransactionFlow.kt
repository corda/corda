package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.FetchDataFlow
import net.corda.core.internal.readFully
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * The [SendTransactionFlow] should be used to send a transaction to another peer that wishes to verify that transaction's
 * integrity by resolving and checking the dependencies as well. The other side should invoke [ReceiveTransactionFlow] at
 * the right point in the conversation to receive the sent transaction and perform the resolution back-and-forth required
 * to check the dependencies and download any missing attachments.
 *
 * @param otherSide the target party.
 * @param stx the [SignedTransaction] being sent to the [otherSideSession].
 */
open class SendTransactionFlow(otherSide: FlowSession, stx: SignedTransaction) : DataVendingFlow(otherSide, stx)

/**
 * The [SendStateAndRefFlow] should be used to send a list of input [StateAndRef] to another peer that wishes to verify
 * the input's integrity by resolving and checking the dependencies as well. The other side should invoke [ReceiveStateAndRefFlow]
 * at the right point in the conversation to receive the input state and ref and perform the resolution back-and-forth
 * required to check the dependencies.
 *
 * @param otherSideSession the target session.
 * @param stateAndRefs the list of [StateAndRef] being sent to the [otherSideSession].
 */
open class SendStateAndRefFlow(otherSideSession: FlowSession, stateAndRefs: List<StateAndRef<*>>) : DataVendingFlow(otherSideSession, stateAndRefs)

open class DataVendingFlow(val otherSideSession: FlowSession, val payload: Any) : FlowLogic<Void?>() {
    @Suspendable
    protected open fun sendPayloadAndReceiveDataRequest(otherSideSession: FlowSession, payload: Any) = otherSideSession.sendAndReceive<FetchDataFlow.Request>(payload)

    @Suspendable
    protected open fun verifyDataRequest(dataRequest: FetchDataFlow.Request.Data) {
        // User can override this method to perform custom request verification.
    }

    @Suspendable
    override fun call(): Void? {
        // The first payload will be the transaction data, subsequent payload will be the transaction/attachment data.
        var payload = payload

        // Depending on who called this flow, the type of the payload is different
        // Maintain a list of requests that the caller is allowed to make based on the transactions that she already requested
        // Todo: should we remove a txId from the set once it has been requested? This would keep the list smaller?
        val validRequests = when (payload) {
            is NotarisationPayload -> getValidRequests(payload.signedTransaction)
            is SignedTransaction -> getValidRequests(payload)
            is List<*> -> payload.flatMap { stateAndRef ->
                if(stateAndRef is StateAndRef<*>){
                    getValidRequests(serviceHub.validatedTransactions.getTransaction(stateAndRef.ref.txhash)!!)
                }else{
                    throw Exception("Unknown payload type: ${stateAndRef!!::class.java} ?")
                }
            }
            else -> throw Exception("Unknown payload type: ${payload::class.java} ?")
        }.toMutableSet()

        // This loop will receive [FetchDataFlow.Request] continuously until the `otherSideSession` has all the data they need
        // to resolve the transaction, a [FetchDataFlow.EndRequest] will be sent from the `otherSideSession` to indicate end of
        // data request.
        while (true) {
            val dataRequest = sendPayloadAndReceiveDataRequest(otherSideSession, payload).unwrap { request ->
                when (request) {
                    is FetchDataFlow.Request.Data -> {
                        // Security TODO: Check for abnormally large or malformed data requests
                        verifyDataRequest(request)
                        request
                    }
                    FetchDataFlow.Request.End -> return null
                }
            }

            payload = when (dataRequest.dataType) {
                FetchDataFlow.DataType.TRANSACTION -> dataRequest.hashes.map { txId ->
                    if (txId !in validRequests) {
                        throw FetchDataFlow.IllegalTransactionRequest(txId)
                    }
                    val tx = serviceHub.validatedTransactions.getTransaction(txId)
                            ?: throw FetchDataFlow.HashNotFound(txId)
                    validRequests.addAll(getValidRequests(tx))
                    tx
                }
                FetchDataFlow.DataType.ATTACHMENT -> dataRequest.hashes.map {
                    serviceHub.attachments.openAttachment(it)?.open()?.readFully()
                            ?: throw FetchDataFlow.HashNotFound(it)
                }
            }
        }
    }

    @Suspendable
    private fun getValidRequests(currentPayload: SignedTransaction): Set<SecureHash> = currentPayload.inputs.map { it.txhash }.toSet()
}

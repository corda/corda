package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.node.ServiceHub
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
        // The first payload will be the transaction data, subsequent payload will be the transaction/attachment/network parameters data.
        var payload = payload

        // Depending on who called this flow, the type of the initial payload is different.
        // The authorisation logic is to maintain a dynamic list of transactions that the caller is authorised to make based on the transactions that were made already.
        // Each time an authorised transaction is requested, the input transactions are added to the list.
        // Once a transaction has been requested, it will be removed from the authorised list. This means that it is a protocol violation to request a transaction twice.
        val authorisedTransactions = when (payload) {
            is NotarisationPayload -> RestrictiveTxAuthorisationFilter(payload.signedTransaction.filterInputTransactions(serviceHub))
            is SignedTransaction -> RestrictiveTxAuthorisationFilter(payload.filterInputTransactions(serviceHub))
            is RetrieveAnyTransactionPayload -> TransactionAuthorisationFilterSimple(serviceHub, acceptAll = true)
            is List<*> ->
                // SGX: in which case is this used?
                TransactionAuthorisationFilterSimple(serviceHub, payload.flatMap { stateAndRef ->
                if (stateAndRef is StateAndRef<*>) {
                    getInputTransactions(serviceHub.validatedTransactions.getTransaction(stateAndRef.ref.txhash)!!) + stateAndRef.ref.txhash
                } else {
                    throw Exception("Unknown payload type: ${stateAndRef!!::class.java} ?")
                }
            }.toMutableSet())
            else -> throw Exception("Unknown payload type: ${payload::class.java} ?")
        }

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
                    authorisedTransactions.get(txId)
                }
                FetchDataFlow.DataType.ATTACHMENT -> dataRequest.hashes.map {
                    serviceHub.attachments.openAttachment(it)?.open()?.readFully()
                            ?: throw FetchDataFlow.HashNotFound(it)
                }
                FetchDataFlow.DataType.PARAMETERS -> dataRequest.hashes.map {
                    (serviceHub.networkParametersService as NetworkParametersStorage).lookupSigned(it)
                            ?: throw FetchDataFlow.MissingNetworkParameters(it)
                }
            }
        }
    }

    // SGX: probably needs to refine the filter implementation based on the requesting party
    interface TransactionAuthorisationFilter {
        fun get(txId: SecureHash): SignedTransaction
    }

    private class TransactionAuthorisationFilterSimple(
            val services: ServiceHub,
            val authorisedTransactions: MutableSet<SecureHash> = HashSet(),
            val acceptAll: Boolean = false) : TransactionAuthorisationFilter {

        override fun get(txId: SecureHash): SignedTransaction {
            if (!isAuthorised(txId)) {
                throw FetchDataFlow.IllegalTransactionRequest(txId)
            }
            val tx = services.validatedTransactions.getTransaction(txId)
                    ?: throw FetchDataFlow.HashNotFound(txId)
            removeAuthorised(tx.id)
            addAuthorised(getInputTransactions(tx))
            return tx
        }

        private fun isAuthorised(txId: SecureHash) = acceptAll || authorisedTransactions.contains(txId)

        private fun addAuthorised(txs: Set<SecureHash>): TransactionAuthorisationFilter {
            authorisedTransactions.addAll(txs)
            return this
        }

        private fun removeAuthorised(txId: SecureHash) {
            authorisedTransactions.remove(txId)
        }
    }

    private class RestrictiveTxAuthorisationFilter(
        private val precomputed: Map<SecureHash, SignedTransaction>
    ): TransactionAuthorisationFilter {

        override fun get(txId: SecureHash): SignedTransaction {
            return precomputed[txId] ?:
                    throw FetchDataFlow.IllegalTransactionRequest(txId)
        }
    }

    companion object {
        fun getInputTransactions(tx: SignedTransaction): Set<SecureHash> {
            return tx.inputs.map { it.txhash }.toSet() + tx.references.map { it.txhash }.toSet()
        }
    }
}

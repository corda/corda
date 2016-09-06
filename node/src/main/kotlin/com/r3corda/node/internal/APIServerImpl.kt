package com.r3corda.node.internal

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.linearHeadsOfType
import com.r3corda.core.serialization.SerializedBytes
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.node.api.*
import java.time.LocalDateTime
import javax.ws.rs.core.Response

class APIServerImpl(val node: AbstractNode) : APIServer {

    override fun serverTime(): LocalDateTime = LocalDateTime.now(node.services.clock)

    override fun status(): Response {
        return if (node.started) {
            Response.ok("started").build()
        } else {
            Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("not started").build()
        }
    }

    override fun queryStates(query: StatesQuery): List<StateRef> {
        // We're going to hard code two options here for now and assume that all LinearStates are deals
        // Would like to maybe move to a model where we take something like a JEXL string, although don't want to develop
        // something we can't later implement against a persistent store (i.e. need to pick / build a query engine)
        if (query is StatesQuery.Selection) {
            if (query.criteria is StatesQuery.Criteria.AllDeals) {
                val states = node.services.walletService.linearHeads
                return states.values.map { it.ref }
            } else if (query.criteria is StatesQuery.Criteria.Deal) {
                val states = node.services.walletService.linearHeadsOfType<DealState>().filterValues {
                    it.state.data.ref == query.criteria.ref
                }
                return states.values.map { it.ref }
            }
        }
        return emptyList()
    }

    override fun fetchStates(states: List<StateRef>): Map<StateRef, TransactionState<ContractState>?> {
        return node.services.walletService.statesForRefs(states)
    }

    override fun fetchTransactions(txs: List<SecureHash>): Map<SecureHash, SignedTransaction?> {
        throw UnsupportedOperationException()
    }

    override fun buildTransaction(type: ContractDefRef, steps: List<TransactionBuildStep>): SerializedBytes<WireTransaction> {
        throw UnsupportedOperationException()
    }

    override fun generateTransactionSignature(tx: SerializedBytes<WireTransaction>): DigitalSignature.WithKey {
        throw UnsupportedOperationException()
    }

    override fun commitTransaction(tx: SerializedBytes<WireTransaction>, signatures: List<DigitalSignature.WithKey>): SecureHash {
        throw UnsupportedOperationException()
    }

    override fun invokeProtocolSync(type: ProtocolRef, args: Map<String, Any?>): Any? {
        return invokeProtocolAsync(type, args).get()
    }

    private fun invokeProtocolAsync(type: ProtocolRef, args: Map<String, Any?>): ListenableFuture<out Any?> {
        if (type is ProtocolClassRef) {
            val protocolLogicRef = node.services.protocolLogicRefFactory.createKotlin(type.className, args)
            val protocolInstance = node.services.protocolLogicRefFactory.toProtocolLogic(protocolLogicRef)
            return node.services.startProtocol(type.className, protocolInstance)
        } else {
            throw UnsupportedOperationException("Unsupported ProtocolRef type: $type")
        }
    }

    override fun fetchProtocolsRequiringAttention(query: StatesQuery): Map<StateRef, ProtocolRequiringAttention> {
        throw UnsupportedOperationException()
    }

    override fun provideProtocolResponse(protocol: ProtocolInstanceRef, choice: SecureHash, args: Map<String, Any?>) {
        throw UnsupportedOperationException()
    }

}

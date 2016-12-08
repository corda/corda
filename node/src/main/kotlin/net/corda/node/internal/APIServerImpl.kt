package net.corda.node.internal

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.DealState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.node.api.*
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

    override fun info() = node.services.myInfo

    override fun queryStates(query: StatesQuery): List<StateRef> {
        // We're going to hard code two options here for now and assume that all LinearStates are deals
        // Would like to maybe move to a model where we take something like a JEXL string, although don't want to develop
        // something we can't later implement against a persistent store (i.e. need to pick / build a query engine)
        if (query is StatesQuery.Selection) {
            if (query.criteria is StatesQuery.Criteria.AllDeals) {
                val states = node.services.vaultService.linearHeads
                return states.values.map { it.ref }
            } else if (query.criteria is StatesQuery.Criteria.Deal) {
                val states = node.services.vaultService.linearHeadsOfType<DealState>().filterValues {
                    it.state.data.ref == query.criteria.ref
                }
                return states.values.map { it.ref }
            }
        }
        return emptyList()
    }

    override fun fetchStates(states: List<StateRef>): Map<StateRef, TransactionState<ContractState>?> {
        return node.services.vaultService.statesForRefs(states)
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

    override fun invokeFlowSync(type: FlowRef, args: Map<String, Any?>): Any? {
        return invokeFlowAsync(type, args).get()
    }

    private fun invokeFlowAsync(type: FlowRef, args: Map<String, Any?>): ListenableFuture<out Any?> {
        if (type is FlowClassRef) {
            val flowLogicRef = node.services.flowLogicRefFactory.createKotlin(type.className, args)
            val flowInstance = node.services.flowLogicRefFactory.toFlowLogic(flowLogicRef)
            return node.services.startFlow(flowInstance).resultFuture
        } else {
            throw UnsupportedOperationException("Unsupported FlowRef type: $type")
        }
    }

    override fun fetchFlowsRequiringAttention(query: StatesQuery): Map<StateRef, FlowRequiringAttention> {
        throw UnsupportedOperationException()
    }

    override fun provideFlowResponse(flow: FlowInstanceRef, choice: SecureHash, args: Map<String, Any?>) {
        throw UnsupportedOperationException()
    }

}

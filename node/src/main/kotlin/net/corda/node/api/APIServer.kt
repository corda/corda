package net.corda.node.api

import net.corda.core.contracts.*
import net.corda.node.api.StatesQuery
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import java.time.Instant
import java.time.LocalDateTime
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Top level interface to external interaction with the distributed ledger.
 *
 * Wherever a list is returned by a fetchXXX method that corresponds with an input list, that output list will have optional elements
 * where a null indicates "missing" and the elements returned will be in the order corresponding with the input list.
 *
 */
@Path("")
interface APIServer {

    /**
     * Report current UTC time as understood by the platform.
     */
    @GET
    @Path("servertime")
    @Produces(MediaType.APPLICATION_JSON)
    fun serverTime(): LocalDateTime

    /**
     * Report whether this node is started up or not.
     */
    @GET
    @Path("status")
    @Produces(MediaType.TEXT_PLAIN)
    fun status(): Response

    /**
     * Report this node's configuration and identities.
     * Currently tunnels the NodeInfo as an encoding of the Kryo serialised form.
     * TODO this functionality should be available via the RPC
     */
    @GET
    @Path("info")
    @Produces(MediaType.APPLICATION_JSON)
    fun info(): NodeInfo

    /**
     * Query your "local" states (containing only outputs involving you) and return the hashes & indexes associated with them
     * to probably be later inflated by fetchLedgerTransactions() or fetchStates() although because immutable you can cache them
     * to avoid calling fetchLedgerTransactions() many times.
     *
     * @param query Some "where clause" like expression.
     * @return Zero or more matching States.
     */
    fun queryStates(query: StatesQuery): List<StateRef>

    fun fetchStates(states: List<StateRef>): Map<StateRef, TransactionState<ContractState>?>

    /**
     * Query for immutable transactions (results can be cached indefinitely by their id/hash).
     *
     * @param txs The hashes (from [StateRef.txhash] returned from [queryStates]) you would like full transactions for.
     * @return null values indicate missing transactions from the requested list.
     */
    fun fetchTransactions(txs: List<SecureHash>): Map<SecureHash, SignedTransaction?>

    /**
     * TransactionBuildSteps would be invocations of contract.generateXXX() methods that all share a common TransactionBuilder
     * and a common contract type (e.g. Cash or CommercialPaper)
     * which would automatically be passed as the first argument (we'd need that to be a criteria/pattern of the generateXXX methods).
     */
    fun buildTransaction(type: ContractDefRef, steps: List<TransactionBuildStep>): SerializedBytes<WireTransaction>

    /**
     * Generate a signature for this transaction signed by us.
     */
    fun generateTransactionSignature(tx: SerializedBytes<WireTransaction>): DigitalSignature.WithKey

    /**
     * Attempt to commit transaction (returned from build transaction) with the necessary signatures for that to be
     * successful, otherwise exception is thrown.
     */
    fun commitTransaction(tx: SerializedBytes<WireTransaction>, signatures: List<DigitalSignature.WithKey>): SecureHash

    /**
     * This method would not return until the flow is finished (hence the "Sync").
     *
     * Longer term we'd add an Async version that returns some kind of FlowInvocationRef that could be queried and
     * would appear on some kind of event message that is broadcast informing of progress.
     *
     * Will throw exception if flow fails.
     */
    fun invokeFlowSync(type: FlowRef, args: Map<String, Any?>): Any?

    // fun invokeFlowAsync(type: FlowRef, args: Map<String, Any?>): FlowInstanceRef

    /**
     * Fetch flows that require a response to some prompt/question by a human (on the "bank" side).
     */
    fun fetchFlowsRequiringAttention(query: StatesQuery): Map<StateRef, FlowRequiringAttention>

    /**
     * Provide the response that a flow is waiting for.
     *
     * @param flow Should refer to a previously supplied FlowRequiringAttention.
     * @param stepId Which step of the flow are we referring too.
     * @param choice Should be one of the choices presented in the FlowRequiringAttention.
     * @param args Any arguments required.
     */
    fun provideFlowResponse(flow: FlowInstanceRef, choice: SecureHash, args: Map<String, Any?>)

}

/**
 * Encapsulates the contract type.  e.g. Cash or CommercialPaper etc.
 */
interface ContractDefRef {

}

data class ContractClassRef(val className: String) : ContractDefRef
data class ContractLedgerRef(val hash: SecureHash) : ContractDefRef


/**
 * Encapsulates the flow to be instantiated.  e.g. TwoPartyTradeFlow.Buyer.
 */
interface FlowRef {

}

data class FlowClassRef(val className: String) : FlowRef

data class FlowInstanceRef(val flowInstance: SecureHash, val flowClass: FlowClassRef, val flowStepId: String)

/**
 * Thinking that Instant is OK for short lived flow deadlines.
 */
data class FlowRequiringAttention(val ref: FlowInstanceRef, val prompt: String, val choiceIdsToMessages: Map<SecureHash, String>, val dueBy: Instant)


/**
 * Encapsulate a generateXXX method call on a contract.
 */
data class TransactionBuildStep(val generateMethodName: String, val args: Map<String, Any?>)

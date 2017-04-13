package net.corda.core.messaging

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.StateMachineTransactionMapping
import net.corda.core.node.services.Vault
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import org.bouncycastle.asn1.x500.X500Name
import rx.Observable
import java.io.InputStream
import java.security.PublicKey
import java.time.Instant
import java.util.*

@CordaSerializable
data class StateMachineInfo(
        val id: StateMachineRunId,
        val flowLogicClassName: String,
        val progressTrackerStepAndUpdates: Pair<String, Observable<String>>?
) {
    override fun toString(): String = "${javaClass.simpleName}($id, $flowLogicClassName)"
}

@CordaSerializable
sealed class StateMachineUpdate {
    abstract val id: StateMachineRunId

    data class Added(val stateMachineInfo: StateMachineInfo) : StateMachineUpdate() {
        override val id: StateMachineRunId get() = stateMachineInfo.id
    }

    data class Removed(override val id: StateMachineRunId) : StateMachineUpdate()
}

/**
 * RPC operations that the node exposes to clients using the Java client library. These can be called from
 * client apps and are implemented by the node in the [CordaRPCOpsImpl] class.
 */

// TODO: The use of Pairs throughout is unfriendly for Java interop.

interface CordaRPCOps : RPCOps {
    /**
     * Returns a pair of currently in-progress state machine infos and an observable of future state machine adds/removes.
     */
    @RPCReturnsObservables
    fun stateMachinesAndUpdates(): Pair<List<StateMachineInfo>, Observable<StateMachineUpdate>>

    /**
     * Returns a pair of head states in the vault and an observable of future updates to the vault.
     */
    @RPCReturnsObservables
    fun vaultAndUpdates(): Pair<List<StateAndRef<ContractState>>, Observable<Vault.Update>>

    /**
     * Returns a pair of all recorded transactions and an observable of future recorded ones.
     */
    @RPCReturnsObservables
    fun verifiedTransactions(): Pair<List<SignedTransaction>, Observable<SignedTransaction>>

    /**
     * Returns a snapshot list of existing state machine id - recorded transaction hash mappings, and a stream of future
     * such mappings as well.
     */
    @RPCReturnsObservables
    fun stateMachineRecordedTransactionMapping(): Pair<List<StateMachineTransactionMapping>, Observable<StateMachineTransactionMapping>>

    /**
     * Returns all parties currently visible on the network with their advertised services and an observable of future updates to the network.
     */
    @RPCReturnsObservables
    fun networkMapUpdates(): Pair<List<NodeInfo>, Observable<NetworkMapCache.MapChange>>

    /**
     * Start the given flow with the given arguments, returning an [Observable] with a single observation of the
     * result of running the flow.
     */
    @RPCReturnsObservables
    fun <T : Any> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T>

    /**
     * Returns Node's identity, assuming this will not change while the node is running.
     */
    fun nodeIdentity(): NodeInfo

    /*
     * Add note(s) to an existing Vault transaction
     */
    fun addVaultTransactionNote(txnId: SecureHash, txnNote: String)

    /*
     * Retrieve existing note(s) for a given Vault transaction
     */
    fun getVaultTransactionNotes(txnId: SecureHash): Iterable<String>

    /*
     * Returns a map of how much cash we have in each currency, ignoring details like issuer. Note: currencies for
     * which we have no cash evaluate to null (not present in map), not 0.
     */
    fun getCashBalances(): Map<Currency, Amount<Currency>>

    /**
     * Checks whether an attachment with the given hash is stored on the node.
     */
    fun attachmentExists(id: SecureHash): Boolean

    /**
     * Download an attachment JAR by ID
     */
    fun openAttachment(id: SecureHash): InputStream

    /**
     * Uploads a jar to the node, returns it's hash.
     */
    fun uploadAttachment(jar: InputStream): SecureHash

    // TODO: Remove this from the interface
    @Deprecated("This service will be removed in a future milestone")
    fun uploadFile(dataType: String, name: String?, file: InputStream): String

    /**
     * Authorise a contract state upgrade.
     * This will store the upgrade authorisation in the vault, and will be queried by [ContractUpgradeFlow.Acceptor] during contract upgrade process.
     * Invoking this method indicate the node is willing to upgrade the [state] using the [upgradedContractClass].
     * This method will NOT initiate the upgrade process. To start the upgrade process, see [ContractUpgradeFlow.Instigator].
     */
    fun authoriseContractUpgrade(state: StateAndRef<*>, upgradedContractClass: Class<out UpgradedContract<*, *>>)

    /**
     * Authorise a contract state upgrade.
     * This will remove the upgrade authorisation from the vault.
     */
    fun deauthoriseContractUpgrade(state: StateAndRef<*>)

    /**
     * Returns the node's current time.
     */
    fun currentNodeTime(): Instant

    /**
     * Returns a [ListenableFuture] which completes when the node has registered wih the network map service. It can also
     * complete with an exception if it is unable to.
     */
    @RPCReturnsObservables
    fun waitUntilRegisteredWithNetworkMap(): ListenableFuture<Unit>

    // TODO These need rethinking. Instead of these direct calls we should have a way of replicating a subset of
    // the node's state locally and query that directly.
    /**
     * Returns the [Party] corresponding to the given key, if found.
     */
    fun partyFromKey(key: PublicKey): Party?

    /**
     * Returns the [Party] with the given name as it's [Party.name]
     */
    fun partyFromName(name: String): Party?

    /**
     * Returns the [Party] with the X.500 principal as it's [Party.name]
     */
    fun partyFromX500Name(x500Name: X500Name): Party?

    /** Enumerates the class names of the flows that this node knows about. */
    fun registeredFlows(): List<String>
}

/**
 * These allow type safe invocations of flows from Kotlin, e.g.:
 *
 * val rpc: CordaRPCOps = (..)
 * rpc.startFlow(::ResolveTransactionsFlow, setOf<SecureHash>(), aliceIdentity)
 *
 * Note that the passed in constructor function is only used for unification of other type parameters and reification of
 * the Class instance of the flow. This could be changed to use the constructor function directly.
 */
inline fun <T : Any, reified R : FlowLogic<T>> CordaRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: () -> R
) = startFlowDynamic(R::class.java)

inline fun <T : Any, A, reified R : FlowLogic<T>> CordaRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A) -> R,
        arg0: A
) = startFlowDynamic(R::class.java, arg0)

inline fun <T : Any, A, B, reified R : FlowLogic<T>> CordaRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B) -> R,
        arg0: A,
        arg1: B
) = startFlowDynamic(R::class.java, arg0, arg1)

inline fun <T : Any, A, B, C, reified R : FlowLogic<T>> CordaRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C) -> R,
        arg0: A,
        arg1: B,
        arg2: C
) = startFlowDynamic(R::class.java, arg0, arg1, arg2)

inline fun <T : Any, A, B, C, D, reified R : FlowLogic<T>> CordaRPCOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C, D) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D
) = startFlowDynamic(R::class.java, arg0, arg1, arg2, arg3)

/**
 * [FlowHandle] is a serialisable handle for the started flow, parameterised by the type of the flow's return value.
 *
 * @param id The started state machine's ID.
 * @param progress The stream of progress tracker events.
 * @param returnValue A [ListenableFuture] of the flow's return value.
 */
@CordaSerializable
data class FlowHandle<A>(
        val id: StateMachineRunId,
        val progress: Observable<String>,
        val returnValue: ListenableFuture<A>) : AutoCloseable {

    /**
     * Use this function for flows that returnValue and progress are not going to be used or tracked, so as to free up server resources.
     * Note that it won't really close if one subscribes on progress [Observable], but then forgets to unsubscribe.
     */
    override fun close() {
        returnValue.cancel(false)
        progress.notUsed()
    }
}

/**
 * This function should be invoked on any unwanted Observables returned from RPC to release the server resources.
 * TODO: Delete this function when this file is moved to RPC module, as Observable<T>.notUsed() exists there already.
 *
 * subscribe({}, {}) was used instead of simply calling subscribe()
 * because if an {@code onError} emission arrives (eg. due to an non-correct transaction, such as 'Not sufficient funds')
 * then {@link OnErrorNotImplementedException} is thrown. As we won't handle exceptions from unused Observables,
 * empty inputs are used to subscribe({}, {}).
 */
fun <T> Observable<T>.notUsed() {
    try {
        this.subscribe({}, {}).unsubscribe()
    } catch (e: Exception) {
        // Swallow any other exceptions as well; we won't handle exceptions from unused Observables.
    }
}

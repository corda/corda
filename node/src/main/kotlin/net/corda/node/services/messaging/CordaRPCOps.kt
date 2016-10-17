package net.corda.node.services.messaging

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.StateMachineTransactionMapping
import net.corda.core.node.services.Vault
import net.corda.core.protocols.ProtocolLogic
import net.corda.core.protocols.StateMachineRunId
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.statemachine.ProtocolStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.AddOrRemove
import rx.Observable

data class StateMachineInfo(
        val id: StateMachineRunId,
        val protocolLogicClassName: String,
        val progressTrackerStepAndUpdates: Pair<String, Observable<String>>?
) {
    companion object {
        fun fromProtocolStateMachineImpl(psm: ProtocolStateMachineImpl<*>): StateMachineInfo {
            return StateMachineInfo(
                    id = psm.id,
                    protocolLogicClassName = psm.logic.javaClass.simpleName,
                    progressTrackerStepAndUpdates = psm.logic.track()
            )
        }
    }
}

sealed class StateMachineUpdate(val id: StateMachineRunId) {
    class Added(val stateMachineInfo: StateMachineInfo) : StateMachineUpdate(stateMachineInfo.id)
    class Removed(id: StateMachineRunId) : StateMachineUpdate(id)

    companion object {
        fun fromStateMachineChange(change: StateMachineManager.Change): StateMachineUpdate {
            return when (change.addOrRemove) {
                AddOrRemove.ADD -> {
                    val stateMachineInfo = StateMachineInfo(
                            id = change.id,
                            protocolLogicClassName = change.logic.javaClass.simpleName,
                            progressTrackerStepAndUpdates = change.logic.track()
                    )
                    StateMachineUpdate.Added(stateMachineInfo)
                }
                AddOrRemove.REMOVE -> {
                    StateMachineUpdate.Removed(change.id)
                }
            }
        }
    }
}

/**
 * RPC operations that the node exposes to clients using the Java client library. These can be called from
 * client apps and are implemented by the node in the [CordaRPCOpsImpl] class.
 */
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
     * Start the given protocol with the given arguments, returning an [Observable] with a single observation of the
     * result of running the protocol.
     */
    @RPCReturnsObservables
    fun <T: Any> startProtocolDynamic(logicType: Class<out ProtocolLogic<T>>, vararg args: Any?): ProtocolHandle<T>

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
}

/**
 * These allow type safe invocations of protocols from Kotlin, e.g.:
 *
 * val rpc: CordaRPCOps = (..)
 * rpc.startProtocol(::ResolveTransactionsProtocol, setOf<SecureHash>(), aliceIdentity)
 *
 * Note that the passed in constructor function is only used for unification of other type parameters and reification of
 * the Class instance of the protocol. This could be changed to use the constructor function directly.
 */
inline fun <T : Any, reified R : ProtocolLogic<T>> CordaRPCOps.startProtocol(
        @Suppress("UNUSED_PARAMETER")
        protocolConstructor: () -> R
) = startProtocolDynamic(R::class.java)
inline fun <T : Any, A, reified R : ProtocolLogic<T>> CordaRPCOps.startProtocol(
        @Suppress("UNUSED_PARAMETER")
        protocolConstructor: (A) -> R,
        arg0: A
) = startProtocolDynamic(R::class.java, arg0)
inline fun <T : Any, A, B, reified R : ProtocolLogic<T>> CordaRPCOps.startProtocol(
        @Suppress("UNUSED_PARAMETER")
        protocolConstructor: (A, B) -> R,
        arg0: A,
        arg1: B
) = startProtocolDynamic(R::class.java, arg0, arg1)
inline fun <T : Any, A, B, C, reified R: ProtocolLogic<T>> CordaRPCOps.startProtocol(
        @Suppress("UNUSED_PARAMETER")
        protocolConstructor: (A, B, C) -> R,
        arg0: A,
        arg1: B,
        arg2: C
) = startProtocolDynamic(R::class.java, arg0, arg1, arg2)
inline fun <T : Any, A, B, C, D, reified R : ProtocolLogic<T>> CordaRPCOps.startProtocol(
        @Suppress("UNUSED_PARAMETER")
        protocolConstructor: (A, B, C, D) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D
) = startProtocolDynamic(R::class.java, arg0, arg1, arg2, arg3)

data class ProtocolHandle<A>(
        val id: StateMachineRunId,
        val progress: Observable<ProgressTracker.Change>,
        val returnValue: Observable<A>
)

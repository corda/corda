package net.corda.core.node

import net.corda.core.contracts.*
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowStateMachine
import net.corda.core.messaging.MessagingService
import net.corda.core.node.services.*
import net.corda.core.transactions.SignedTransaction
import java.security.KeyPair
import java.time.Clock

/**
 * A service hub simply vends references to the other services a node has. Some of those services may be missing or
 * mocked out. This class is useful to pass to chunks of pluggable code that might have need of many different kinds of
 * functionality and you don't want to hard-code which types in the interface.
 *
 * Any services exposed to flows (public view) need to implement [SerializeAsToken] or similar to avoid their internal
 * state from being serialized in checkpoints.
 */
interface ServiceHub {
    val vaultService: VaultService
    val keyManagementService: KeyManagementService
    val identityService: IdentityService
    val storageService: StorageService
    val networkService: MessagingService
    val networkMapCache: NetworkMapCache
    val schedulerService: SchedulerService
    val clock: Clock
    val myInfo: NodeInfo

    /**
     * Given a [SignedTransaction], writes it to the local storage for validated transactions and then
     * sends them to the vault for further processing. Expects to be run within a database transaction.
     *
     * @param txs The transactions to record.
     */
    // TODO: Make this take a single tx.
    fun recordTransactions(txs: Iterable<SignedTransaction>)

    /**
     * Given a [StateRef] loads the referenced transaction and looks up the specified output [ContractState].
     *
     * @throws TransactionResolutionException if the [StateRef] points to a non-existent transaction.
     */
    fun loadState(stateRef: StateRef): TransactionState<*> {
        val definingTx = storageService.validatedTransactions.getTransaction(stateRef.txhash) ?: throw TransactionResolutionException(stateRef.txhash)
        return definingTx.tx.outputs[stateRef.index]
    }

    /**
     * Given a [StateRef] loads the referenced transaction and returns a [StateAndRef<T>]
     *
     * @throws TransactionResolutionException if the [StateRef] points to a non-existent transaction.
     */
    fun <T : ContractState> toStateAndRef(ref: StateRef): StateAndRef<T> {
        val definingTx =  storageService.validatedTransactions.getTransaction(ref.txhash) ?: throw TransactionResolutionException(ref.txhash)
        return definingTx.tx.outRef<T>(ref.index)
    }

    /**
     * Will check [logicType] and [args] against a whitelist and if acceptable then construct and initiate the flow.
     * Note that you must be on the server thread to call this method.
     *
     * @throws IllegalFlowLogicException or IllegalArgumentException if there are problems with the [logicType] or [args].
     */
    fun <T : Any> invokeFlowAsync(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowStateMachine<T>

    /**
     * Helper property to shorten code for fetching the Node's KeyPair associated with the
     * public legalIdentity Party from the key management service.
     * Typical use is during signing in flows and for unit test signing.
     *
     * TODO: legalIdentity can now be composed of multiple keys, should we return a list of keyPairs here? Right now
     *       the logic assumes the legal identity has a composite key with only one node
     */
    val legalIdentityKey: KeyPair get() = this.keyManagementService.toKeyPair(this.myInfo.legalIdentity.owningKey.keys)

    /**
     * Helper property to shorten code for fetching the Node's KeyPair associated with the
     * public notaryIdentity Party from the key management service. It is assumed that this is only
     * used in contexts where the Node knows it is hosting a Notary Service. Otherwise, it will throw
     * an IllegalArgumentException.
     * Typical use is during signing in flows and for unit test signing.
     */
    val notaryIdentityKey: KeyPair get() = this.keyManagementService.toKeyPair(this.myInfo.notaryIdentity.owningKey.keys)
}

/**
 * Given some [SignedTransaction]s, writes them to the local storage for validated transactions and then
 * sends them to the vault for further processing.
 *
 * @param txs The transactions to record.
 */
fun ServiceHub.recordTransactions(vararg txs: SignedTransaction) = recordTransactions(txs.toList())

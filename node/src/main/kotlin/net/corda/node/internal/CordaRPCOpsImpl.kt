package net.corda.node.internal

import com.google.common.util.concurrent.ListenableFuture
import net.corda.client.rpc.notUsed
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.StateMachineTransactionMapping
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.requirePermission
import net.corda.node.services.startFlowPermission
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.transaction
import net.corda.nodeapi.CURRENT_RPC_USER
import org.bouncycastle.asn1.x500.X500Name
import org.jetbrains.exposed.sql.Database
import rx.Observable
import java.io.InputStream
import java.security.PublicKey
import java.time.Instant
import java.util.*

/**
 * Server side implementations of RPCs available to MQ based client tools. Execution takes place on the server
 * thread (i.e. serially). Arguments are serialised and deserialised automatically.
 */
class CordaRPCOpsImpl(
        private val services: ServiceHubInternal,
        private val smm: StateMachineManager,
        private val database: Database
) : CordaRPCOps {
    override val protocolVersion: Int = 0

    override fun networkMapUpdates(): Pair<List<NodeInfo>, Observable<NetworkMapCache.MapChange>> {
        return database.transaction {
            services.networkMapCache.track()
        }
    }

    override fun vaultAndUpdates(): Pair<List<StateAndRef<ContractState>>, Observable<Vault.Update>> {
        return database.transaction {
            val (vault, updates) = services.vaultService.track()
            Pair(vault.states.toList(), updates)
        }
    }

    override fun <T : ContractState> vaultQueryBy(criteria: QueryCriteria,
                                                  paging: PageSpecification,
                                                  sorting: Sort): Vault.Page<T> {
        return database.transaction {
            services.vaultService.queryBy<T>(criteria, paging, sorting)
        }
    }

    @RPCReturnsObservables
    override fun <T : ContractState> vaultTrackBy(criteria: QueryCriteria,
                                                  paging: PageSpecification,
                                                  sorting: Sort): Vault.PageAndUpdates<T> {
        return database.transaction {
            services.vaultService.trackBy<T>(criteria, paging, sorting)
        }
    }

    override fun verifiedTransactions(): Pair<List<SignedTransaction>, Observable<SignedTransaction>> {
        return database.transaction {
            services.storageService.validatedTransactions.track()
        }
    }

    override fun stateMachinesAndUpdates(): Pair<List<StateMachineInfo>, Observable<StateMachineUpdate>> {
        return database.transaction {
            val (allStateMachines, changes) = smm.track()
            Pair(
                    allStateMachines.map { stateMachineInfoFromFlowLogic(it.logic) },
                    changes.map { stateMachineUpdateFromStateMachineChange(it) }
            )
        }
    }

    override fun stateMachineRecordedTransactionMapping(): Pair<List<StateMachineTransactionMapping>, Observable<StateMachineTransactionMapping>> {
        return database.transaction {
            services.storageService.stateMachineRecordedTransactionMapping.track()
        }
    }

    override fun nodeIdentity(): NodeInfo {
        return services.myInfo
    }

    override fun addVaultTransactionNote(txnId: SecureHash, txnNote: String) {
        return database.transaction {
            services.vaultService.addNoteToTransaction(txnId, txnNote)
        }
    }

    override fun getVaultTransactionNotes(txnId: SecureHash): Iterable<String> {
        return database.transaction {
            services.vaultService.getTransactionNotes(txnId)
        }
    }

    override fun getCashBalances(): Map<Currency, Amount<Currency>> {
        return database.transaction {
            services.vaultService.cashBalances
        }
    }

    // TODO: Check that this flow is annotated as being intended for RPC invocation
    override fun <T : Any> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> {
        requirePermission(startFlowPermission(logicType))
        val currentUser = FlowInitiator.RPC(CURRENT_RPC_USER.get().username)
        val stateMachine = services.invokeFlowAsync(logicType, currentUser, *args)
        return FlowProgressHandleImpl(
                id = stateMachine.id,
                returnValue = stateMachine.resultFuture,
                progress = stateMachine.logic.track()?.second ?: Observable.empty()
        )
    }

    // TODO: Check that this flow is annotated as being intended for RPC invocation
    override fun <T : Any> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T> {
        requirePermission(startFlowPermission(logicType))
        val currentUser = FlowInitiator.RPC(CURRENT_RPC_USER.get().username)
        val stateMachine = services.invokeFlowAsync(logicType, currentUser, *args)
        return FlowHandleImpl(id = stateMachine.id, returnValue = stateMachine.resultFuture)
    }

    override fun attachmentExists(id: SecureHash): Boolean {
        // TODO: this operation should not require an explicit transaction
        return database.transaction {
            services.storageService.attachments.openAttachment(id) != null
        }
    }

    override fun openAttachment(id: SecureHash): InputStream {
        // TODO: this operation should not require an explicit transaction
        return database.transaction {
            services.storageService.attachments.openAttachment(id)!!.open()
        }
    }

    override fun uploadAttachment(jar: InputStream): SecureHash {
        // TODO: this operation should not require an explicit transaction
        return database.transaction {
            services.storageService.attachments.importAttachment(jar)
        }
    }

    override fun authoriseContractUpgrade(state: StateAndRef<*>, upgradedContractClass: Class<out UpgradedContract<*, *>>) = services.vaultService.authoriseContractUpgrade(state, upgradedContractClass)
    override fun deauthoriseContractUpgrade(state: StateAndRef<*>) = services.vaultService.deauthoriseContractUpgrade(state)
    override fun currentNodeTime(): Instant = Instant.now(services.clock)
    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun uploadFile(dataType: String, name: String?, file: InputStream): String {
        val acceptor = services.storageService.uploaders.firstOrNull { it.accepts(dataType) }
        return database.transaction {
            acceptor?.upload(file) ?: throw RuntimeException("Cannot find file upload acceptor for $dataType")
        }
    }

    override fun waitUntilRegisteredWithNetworkMap() = services.networkMapCache.mapServiceRegistered
    override fun partyFromKey(key: PublicKey) = services.identityService.partyFromKey(key)
    @Deprecated("Use partyFromX500Name instead")
    override fun partyFromName(name: String) = services.identityService.partyFromName(name)
    override fun partyFromX500Name(x500Name: X500Name)= services.identityService.partyFromX500Name(x500Name)

    override fun registeredFlows(): List<String> = services.flowLogicRefFactory.flowWhitelist.keys.sorted()

    companion object {
        private fun stateMachineInfoFromFlowLogic(flowLogic: FlowLogic<*>): StateMachineInfo {
            return StateMachineInfo(flowLogic.runId, flowLogic.javaClass.name, flowLogic.stateMachine.flowInitiator, flowLogic.track())
        }

        private fun stateMachineUpdateFromStateMachineChange(change: StateMachineManager.Change): StateMachineUpdate {
            return when (change) {
                is StateMachineManager.Change.Add -> StateMachineUpdate.Added(stateMachineInfoFromFlowLogic(change.logic))
                is StateMachineManager.Change.Removed -> StateMachineUpdate.Removed(change.logic.runId, change.result)
            }
        }
    }

    // I would prefer for [FlowProgressHandleImpl] to extend [FlowHandleImpl],
    // but Kotlin doesn't allow this for data classes, not even to create
    // another data class!
    @CordaSerializable
    private data class FlowHandleImpl<A>(
            override val id: StateMachineRunId,
            override val returnValue: ListenableFuture<A>) : FlowHandle<A> {

        // Remember to add @Throws to FlowHandle.close if this throws an exception
        override fun close() {
            returnValue.cancel(false)
        }
    }

    @CordaSerializable
    private data class FlowProgressHandleImpl<A>(
            override val id: StateMachineRunId,
            override val returnValue: ListenableFuture<A>,
            override val progress: Observable<String>) : FlowProgressHandle<A> {

        // Remember to add @Throws to FlowProgressHandle.close if this throws an exception
        override fun close() {
            progress.notUsed()
            returnValue.cancel(false)
        }
    }
}

package net.corda.node.internal

import net.corda.client.rpc.notUsed
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.NodeLookup
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.getRpcContext
import net.corda.node.services.messaging.requirePermission
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.CordaPersistence
import rx.Observable
import java.io.InputStream
import java.security.PublicKey
import java.time.Instant

/**
 * Server side implementations of RPCs available to MQ based client tools. Execution takes place on the server
 * thread (i.e. serially). Arguments are serialised and deserialised automatically.
 */
class CordaRPCOpsImpl(
        private val services: ServiceHubInternal,
        private val smm: StateMachineManager,
        private val database: CordaPersistence,
        private val nodeLookup: NodeLookup
) : CordaRPCOps {
    override fun networkMapSnapshot(): List<NodeInfo> {
        val (snapshot, updates) = networkMapFeed()
        updates.notUsed()
        return snapshot
    }

    override fun networkMapFeed(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> {
        return database.transaction {
            services.networkMapCache.track()
        }
    }

    override fun <T : ContractState> vaultQueryBy(criteria: QueryCriteria,
                                                  paging: PageSpecification,
                                                  sorting: Sort,
                                                  contractStateType: Class<out T>): Vault.Page<T> {
        return database.transaction {
            services.vaultService._queryBy(criteria, paging, sorting, contractStateType)
        }
    }

    @RPCReturnsObservables
    override fun <T : ContractState> vaultTrackBy(criteria: QueryCriteria,
                                                  paging: PageSpecification,
                                                  sorting: Sort,
                                                  contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return database.transaction {
            services.vaultService._trackBy(criteria, paging, sorting, contractStateType)
        }
    }

    override fun internalVerifiedTransactionsSnapshot(): List<SignedTransaction> {
        val (snapshot, updates) = internalVerifiedTransactionsFeed()
        updates.notUsed()
        return snapshot
    }

    override fun internalVerifiedTransactionsFeed(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return database.transaction {
            services.validatedTransactions.track()
        }
    }

    override fun stateMachinesSnapshot(): List<StateMachineInfo> {
        val (snapshot, updates) = stateMachinesFeed()
        updates.notUsed()
        return snapshot
    }

    override fun stateMachinesFeed(): DataFeed<List<StateMachineInfo>, StateMachineUpdate> {
        return database.transaction {
            val (allStateMachines, changes) = smm.track()
            DataFeed(
                    allStateMachines.map { stateMachineInfoFromFlowLogic(it.logic) },
                    changes.map { stateMachineUpdateFromStateMachineChange(it) }
            )
        }
    }

    override fun stateMachineRecordedTransactionMappingSnapshot(): List<StateMachineTransactionMapping> {
        val (snapshot, updates) = stateMachineRecordedTransactionMappingFeed()
        updates.notUsed()
        return snapshot
    }

    override fun stateMachineRecordedTransactionMappingFeed(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> {
        return database.transaction {
            services.stateMachineRecordedTransactionMapping.track()
        }
    }

    override fun nodeInfo(): NodeInfo {
        return services.myInfo
    }

    override fun notaryIdentities(): List<Party> {
        return services.networkMapCache.notaryIdentities
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

    override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> {
        val stateMachine = startFlow(logicType, args)
        return FlowProgressHandleImpl(
                id = stateMachine.id,
                returnValue = stateMachine.resultFuture,
                progress = stateMachine.logic.track()?.updates ?: Observable.empty()
        )
    }

    override fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T> {
        val stateMachine = startFlow(logicType, args)
        return FlowHandleImpl(id = stateMachine.id, returnValue = stateMachine.resultFuture)
    }

    private fun <T> startFlow(logicType: Class<out FlowLogic<T>>, args: Array<out Any?>): FlowStateMachineImpl<T> {
        require(logicType.isAnnotationPresent(StartableByRPC::class.java)) { "${logicType.name} was not designed for RPC" }
        val rpcContext = getRpcContext()
        rpcContext.requirePermission(startFlowPermission(logicType))
        val currentUser = FlowInitiator.RPC(rpcContext.currentUser.username)
        // TODO RPC flows should have mapping user -> identity that should be resolved automatically on starting flow.
        return services.invokeFlowAsync(logicType, currentUser, *args)
    }

    override fun attachmentExists(id: SecureHash): Boolean {
        // TODO: this operation should not require an explicit transaction
        return database.transaction {
            services.attachments.openAttachment(id) != null
        }
    }

    override fun openAttachment(id: SecureHash): InputStream {
        // TODO: this operation should not require an explicit transaction
        return database.transaction {
            services.attachments.openAttachment(id)!!.open()
        }
    }

    override fun uploadAttachment(jar: InputStream): SecureHash {
        // TODO: this operation should not require an explicit transaction
        return database.transaction {
            services.attachments.importAttachment(jar)
        }
    }

    override fun currentNodeTime(): Instant = Instant.now(services.clock)

    override fun waitUntilNetworkReady(): CordaFuture<Void?> {
        return database.transaction {
            services.networkMapCache.nodeReady
        }
    }

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        return database.transaction {
            services.identityService.wellKnownPartyFromAnonymous(party)
        }
    }

    override fun partyFromKey(key: PublicKey): Party? {
        return database.transaction {
            services.identityService.partyFromKey(key)
        }
    }

    override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name): Party? {
        return database.transaction {
            services.identityService.wellKnownPartyFromX500Name(x500Name)
        }
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        return database.transaction {
            services.identityService.partiesFromName(query, exactMatch)
        }
    }

    override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? {
        return database.transaction {
            nodeLookup.getNodeByLegalIdentity(party)
        }
    }

    override fun registeredFlows(): List<String> = services.rpcFlows.map { it.name }.sorted()

    override fun clearNetworkMapCache() {
        database.transaction {
            services.networkMapCache.clearNetworkMapCache()
        }
    }

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

}


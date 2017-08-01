package net.corda.node.internal

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.getRpcContext
import net.corda.node.services.messaging.requirePermission
import net.corda.node.services.startFlowPermission
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.transaction
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
    override fun networkMapFeed(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> {
        return database.transaction {
            services.networkMapCache.track()
        }
    }

    override fun vaultAndUpdates(): DataFeed<List<StateAndRef<ContractState>>, Vault.Update> {
        return database.transaction {
            val (vault, updates) = services.vaultService.track()
            DataFeed(vault.states.toList(), updates)
        }
    }

    override fun <T : ContractState> vaultQueryBy(criteria: QueryCriteria,
                                                  paging: PageSpecification,
                                                  sorting: Sort,
                                                  contractType: Class<out T>): Vault.Page<T> {
        return database.transaction {
            services.vaultQueryService._queryBy(criteria, paging, sorting, contractType)
        }
    }

    @RPCReturnsObservables
    override fun <T : ContractState> vaultTrackBy(criteria: QueryCriteria,
                                                  paging: PageSpecification,
                                                  sorting: Sort,
                                                  contractType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update> {
        return database.transaction {
            services.vaultQueryService._trackBy(criteria, paging, sorting, contractType)
        }
    }

    override fun verifiedTransactionsFeed(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return database.transaction {
            services.validatedTransactions.track()
        }
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

    override fun stateMachineRecordedTransactionMappingFeed(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> {
        return database.transaction {
            services.stateMachineRecordedTransactionMapping.track()
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

    override fun <T : Any> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> {
        val stateMachine = startFlow(logicType, args)
        return FlowProgressHandleImpl(
                id = stateMachine.id,
                returnValue = stateMachine.resultFuture,
                progress = stateMachine.logic.track()?.second ?: Observable.empty()
        )
    }

    override fun <T : Any> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T> {
        val stateMachine = startFlow(logicType, args)
        return FlowHandleImpl(id = stateMachine.id, returnValue = stateMachine.resultFuture)
    }

    private fun <T : Any> startFlow(logicType: Class<out FlowLogic<T>>, args: Array<out Any?>): FlowStateMachineImpl<T> {
        require(logicType.isAnnotationPresent(StartableByRPC::class.java)) { "${logicType.name} was not designed for RPC" }
        val rpcContext = getRpcContext()
        rpcContext.requirePermission(startFlowPermission(logicType))
        val currentUser = FlowInitiator.RPC(rpcContext.currentUser.username)
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

    override fun authoriseContractUpgrade(state: StateAndRef<*>, upgradedContractClass: Class<out UpgradedContract<*, *>>) = services.vaultService.authoriseContractUpgrade(state, upgradedContractClass)
    override fun deauthoriseContractUpgrade(state: StateAndRef<*>) = services.vaultService.deauthoriseContractUpgrade(state)
    override fun currentNodeTime(): Instant = Instant.now(services.clock)
    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun uploadFile(dataType: String, name: String?, file: InputStream): String {
        val acceptor = services.uploaders.firstOrNull { it.accepts(dataType) }
        return database.transaction {
            acceptor?.upload(file) ?: throw RuntimeException("Cannot find file upload acceptor for $dataType")
        }
    }

    override fun waitUntilRegisteredWithNetworkMap() = services.networkMapCache.mapServiceRegistered
    override fun partyFromKey(key: PublicKey) = services.identityService.partyFromKey(key)
    @Suppress("DEPRECATION")
    @Deprecated("Use partyFromX500Name instead")
    override fun partyFromName(name: String) = services.identityService.partyFromName(name)
    override fun partyFromX500Name(x500Name: X500Name) = services.identityService.partyFromX500Name(x500Name)
    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> = services.identityService.partiesFromName(query, exactMatch)
    override fun nodeIdentityFromParty(party: AbstractParty): NodeInfo? = services.networkMapCache.getNodeByLegalIdentity(party)

    override fun registeredFlows(): List<String> = services.rpcFlows.map { it.name }.sorted()

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


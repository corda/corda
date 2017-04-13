package net.corda.node.internal

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.StateMachineInfo
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.StateMachineTransactionMapping
import net.corda.core.node.services.Vault
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.messaging.requirePermission
import net.corda.node.services.startFlowPermission
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.AddOrRemove
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
    override val protocolVersion: Int get() = 0

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

    override fun verifiedTransactions(): Pair<List<SignedTransaction>, Observable<SignedTransaction>> {
        return database.transaction {
            services.storageService.validatedTransactions.track()
        }
    }

    override fun stateMachinesAndUpdates(): Pair<List<StateMachineInfo>, Observable<StateMachineUpdate>> {
        return database.transaction {
            val (allStateMachines, changes) = smm.track()
            Pair(
                    allStateMachines.map { stateMachineInfoFromFlowLogic(it.id, it.logic) },
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
    override fun <T : Any> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T> {
        requirePermission(startFlowPermission(logicType))
        val stateMachine = services.invokeFlowAsync(logicType, *args) as FlowStateMachineImpl<T>
        return FlowHandle(
                id = stateMachine.id,
                progress = stateMachine.logic.track()?.second ?: Observable.empty(),
                returnValue = stateMachine.resultFuture
        )
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
    override fun partyFromName(name: String) = services.identityService.partyFromName(name)
    override fun partyFromX500Name(x500Name: X500Name)= services.identityService.partyFromX500Name(x500Name)

    override fun registeredFlows(): List<String> = services.flowLogicRefFactory.flowWhitelist.keys.sorted()

    companion object {
        private fun stateMachineInfoFromFlowLogic(id: StateMachineRunId, flowLogic: FlowLogic<*>): StateMachineInfo {
            return StateMachineInfo(id, flowLogic.javaClass.name, flowLogic.track())
        }

        private fun stateMachineUpdateFromStateMachineChange(change: StateMachineManager.Change): StateMachineUpdate {
            return when (change.addOrRemove) {
                AddOrRemove.ADD -> StateMachineUpdate.Added(stateMachineInfoFromFlowLogic(change.id, change.logic))
                AddOrRemove.REMOVE -> StateMachineUpdate.Removed(change.id)
            }
        }
    }
}

package net.corda.node.shell

import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.security.AuthorizingSubject
import net.corda.node.services.messaging.CURRENT_RPC_CONTEXT
import net.corda.node.services.messaging.RpcAuthContext
import rx.Observable
import java.io.InputStream
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class RPCOpsWithContext(val cordaRPCOps: CordaRPCOps, val invocationContext:InvocationContext, val authorizingSubject: AuthorizingSubject) : CordaRPCOps {


    class RPCContextRunner<T>(val invocationContext:InvocationContext, val authorizingSubject: AuthorizingSubject, val block:() -> T) : Thread() {
        private var result: CompletableFuture<T> = CompletableFuture()
        override fun run() {
            CURRENT_RPC_CONTEXT.set(RpcAuthContext(invocationContext, authorizingSubject))
            try {
                result.complete(block())
            } catch (e:Throwable) {
                result.completeExceptionally(e)
            }
            CURRENT_RPC_CONTEXT.remove()
        }

        fun get(): Future<T> {
            start()
            join()
            return result
        }
    }

    override fun uploadAttachmentWithMetadata(jar: InputStream, uploader: String, filename: String): SecureHash {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.uploadAttachmentWithMetadata(jar, uploader, filename) }.get().getOrThrow()
    }

    override fun queryAttachments(query: AttachmentQueryCriteria, sorting: AttachmentSort?): List<AttachmentId> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.queryAttachments(query, sorting) }.get().getOrThrow()
    }

    override fun nodeStateObservable(): Observable<NodeState> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.nodeStateObservable() }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultTrackByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultTrackByWithSorting(contractStateType, criteria, sorting) }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultTrackByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultTrackByWithPagingSpec(contractStateType, criteria, paging) }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultTrackByCriteria(contractStateType: Class<out T>, criteria: QueryCriteria): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultTrackByCriteria(contractStateType, criteria) }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultTrack(contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultTrack(contractStateType) }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultQueryByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort): Vault.Page<T> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultQueryByWithSorting(contractStateType, criteria, sorting) }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultQueryByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): Vault.Page<T> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultQueryByWithPagingSpec(contractStateType, criteria, paging) }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultQueryByCriteria(criteria: QueryCriteria, contractStateType: Class<out T>): Vault.Page<T> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultQueryByCriteria(criteria, contractStateType) }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultQuery(contractStateType: Class<out T>): Vault.Page<T> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultQuery(contractStateType) }.get().getOrThrow()
    }

    override fun stateMachinesSnapshot(): List<StateMachineInfo> {
        return RPCContextRunner(invocationContext, authorizingSubject, cordaRPCOps::stateMachinesSnapshot).get().getOrThrow()
    }

    override fun stateMachinesFeed(): DataFeed<List<StateMachineInfo>, StateMachineUpdate> {
        return RPCContextRunner(invocationContext, authorizingSubject, cordaRPCOps::stateMachinesFeed).get().getOrThrow()
    }

    override fun <T : ContractState> vaultQueryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): Vault.Page<T> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultQueryBy(criteria, paging, sorting, contractStateType) }.get().getOrThrow()
    }

    override fun <T : ContractState> vaultTrackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.vaultTrackBy(criteria, paging, sorting, contractStateType) }.get().getOrThrow()
    }

    override fun internalVerifiedTransactionsSnapshot(): List<SignedTransaction> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.internalVerifiedTransactionsSnapshot() }.get().getOrThrow()
    }

    override fun internalVerifiedTransactionsFeed(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.internalVerifiedTransactionsFeed() }.get().getOrThrow()
    }

    override fun stateMachineRecordedTransactionMappingSnapshot(): List<StateMachineTransactionMapping> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.stateMachineRecordedTransactionMappingSnapshot() }.get().getOrThrow()
    }

    override fun stateMachineRecordedTransactionMappingFeed(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.stateMachineRecordedTransactionMappingFeed() }.get().getOrThrow()
    }

    override fun networkMapSnapshot(): List<NodeInfo> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.networkMapSnapshot() }.get().getOrThrow()
    }

    override fun networkMapFeed(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.networkMapFeed() }.get().getOrThrow()
    }

    override fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.startFlowDynamic(logicType, *args) }.get().getOrThrow()
    }

    override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.startTrackedFlowDynamic(logicType, *args) }.get().getOrThrow()
    }

    override fun nodeInfo(): NodeInfo {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.nodeInfo() }.get().getOrThrow()
    }

    override fun notaryIdentities(): List<Party> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.notaryIdentities() }.get().getOrThrow()
    }

    override fun addVaultTransactionNote(txnId: SecureHash, txnNote: String) {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.addVaultTransactionNote(txnId, txnNote) }.get().getOrThrow()
    }

    override fun getVaultTransactionNotes(txnId: SecureHash): Iterable<String> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.getVaultTransactionNotes(txnId) }.get().getOrThrow()
    }

    override fun attachmentExists(id: SecureHash): Boolean {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.attachmentExists(id) }.get().getOrThrow()
    }

    override fun openAttachment(id: SecureHash): InputStream {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.openAttachment(id) }.get().getOrThrow()
    }

    override fun uploadAttachment(jar: InputStream): SecureHash {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.uploadAttachment(jar) }.get().getOrThrow()
    }

    override fun currentNodeTime(): Instant {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.currentNodeTime() }.get().getOrThrow()
    }

    override fun waitUntilNetworkReady(): CordaFuture<Void?> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.waitUntilNetworkReady() }.get().getOrThrow()
    }

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.wellKnownPartyFromAnonymous(party) }.get().getOrThrow()
    }

    override fun partyFromKey(key: PublicKey): Party? {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.partyFromKey(key) }.get().getOrThrow()
    }

    override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name): Party? {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.wellKnownPartyFromX500Name(x500Name) }.get().getOrThrow()
    }

    override fun notaryPartyFromX500Name(x500Name: CordaX500Name): Party? {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.notaryPartyFromX500Name(x500Name) }.get().getOrThrow()
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.partiesFromName(query, exactMatch) }.get().getOrThrow()
    }

    override fun registeredFlows(): List<String> {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.registeredFlows() }.get().getOrThrow()
    }

    override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.nodeInfoFromParty(party) }.get().getOrThrow()
    }

    override fun clearNetworkMapCache() {
        return RPCContextRunner(invocationContext, authorizingSubject) { cordaRPCOps.clearNetworkMapCache() }.get().getOrThrow()
    }
}
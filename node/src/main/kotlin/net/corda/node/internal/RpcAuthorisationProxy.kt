package net.corda.node.internal

import net.corda.client.rpc.PermissionException
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.node.services.messaging.RpcContext
import net.corda.node.services.messaging.rpcContext
import java.io.InputStream
import java.security.PublicKey
import net.corda.node.services.security.RPCPermission
import net.corda.node.services.security.RPCPermission.Companion.invokeRpc
import org.apache.shiro.authz.permission.DomainPermission

class RpcAuthorisationProxy(
        private val implementation: CordaRPCOps,
        private val context: () -> RpcContext) : CordaRPCOps {

    override fun stateMachinesSnapshot() =
        guard(invokeRpc(CordaRPCOps::stateMachinesSnapshot),
              implementation::stateMachinesSnapshot)

    override fun stateMachinesFeed() =
        guard(invokeRpc(CordaRPCOps::stateMachinesFeed),
              implementation::stateMachinesFeed)

    override fun <T : ContractState> vaultQueryBy(
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort,
        contractStateType: Class<out T>) = guard(invokeRpc("vaultQueryBy")) {
            implementation.vaultQueryBy(criteria, paging, sorting, contractStateType)
        }

    override fun <T : ContractState> vaultTrackBy(
        criteria: QueryCriteria,
        paging: PageSpecification,
        sorting: Sort,
        contractStateType: Class<out T>) = guard(invokeRpc("vaultTrackBy")) {
            implementation.vaultTrackBy(criteria, paging, sorting, contractStateType)
        }

    override fun internalVerifiedTransactionsSnapshot() =
        guard(invokeRpc(CordaRPCOps::internalVerifiedTransactionsSnapshot),
              implementation::internalVerifiedTransactionsSnapshot)

    override fun internalVerifiedTransactionsFeed() =
        guard(invokeRpc(CordaRPCOps::internalVerifiedTransactionsFeed),
              implementation::internalVerifiedTransactionsFeed)

    override fun stateMachineRecordedTransactionMappingSnapshot() =
        guard(invokeRpc(CordaRPCOps::stateMachineRecordedTransactionMappingSnapshot),
              implementation::stateMachineRecordedTransactionMappingSnapshot)

    override fun stateMachineRecordedTransactionMappingFeed() =
        guard(invokeRpc(CordaRPCOps::stateMachineRecordedTransactionMappingFeed),
              implementation::stateMachineRecordedTransactionMappingFeed)

    override fun networkMapSnapshot() =
        guard(invokeRpc(CordaRPCOps::networkMapSnapshot),
              implementation::networkMapSnapshot)

    override fun networkMapFeed() =
        guard(invokeRpc(CordaRPCOps::networkMapFeed),
              implementation::networkMapFeed)

    override fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?) =
        guard(invokeRpc("startFlowDynamic").onTarget(logicType)) {
            implementation.startFlowDynamic(logicType, *args)
        }

    override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?) =
        guard(invokeRpc("startTrackedFlowDynamic").onTarget(logicType)) {
            implementation.startTrackedFlowDynamic(logicType, *args)
        }

    override fun nodeInfo() =
        guard(invokeRpc(CordaRPCOps::nodeInfo), implementation::nodeInfo)

    override fun notaryIdentities() =
        guard(invokeRpc(CordaRPCOps::notaryIdentities), implementation::notaryIdentities)

    override fun addVaultTransactionNote(txnId: SecureHash, txnNote: String) =
        guard(invokeRpc(CordaRPCOps::addVaultTransactionNote)) {
            implementation.addVaultTransactionNote(txnId, txnNote)
        }

    override fun getVaultTransactionNotes(txnId: SecureHash) =
        guard(invokeRpc(CordaRPCOps::getVaultTransactionNotes)) {
            implementation.getVaultTransactionNotes(txnId)
        }

    override fun attachmentExists(id: SecureHash) =
        guard(invokeRpc(CordaRPCOps::attachmentExists)) {
            implementation.attachmentExists(id)
        }

    override fun openAttachment(id: SecureHash) =
        guard(invokeRpc(CordaRPCOps::openAttachment)) {
           implementation.openAttachment(id)
        }

    override fun uploadAttachment(jar: InputStream) =
        guard(invokeRpc(CordaRPCOps::uploadAttachment)) {
            implementation.uploadAttachment(jar)
        }

    override fun currentNodeTime() =
        guard(invokeRpc(CordaRPCOps::currentNodeTime),
              implementation::currentNodeTime)

    override fun waitUntilNetworkReady() =
        guard(invokeRpc(CordaRPCOps::waitUntilNetworkReady),
              implementation::waitUntilNetworkReady)

    override fun wellKnownPartyFromAnonymous(party: AbstractParty) = 
        guard(invokeRpc(CordaRPCOps::wellKnownPartyFromAnonymous)) {
            implementation.wellKnownPartyFromAnonymous(party)
        }

    override fun partyFromKey(key: PublicKey) = 
        guard(invokeRpc(CordaRPCOps::partyFromKey)) {
            implementation.partyFromKey(key)
        }

    override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name) =
        guard(invokeRpc(CordaRPCOps::wellKnownPartyFromX500Name)) {
            implementation.wellKnownPartyFromX500Name(x500Name)
        }

    override fun notaryPartyFromX500Name(x500Name: CordaX500Name) =
        guard(invokeRpc(CordaRPCOps::notaryPartyFromX500Name)) {
            implementation.notaryPartyFromX500Name(x500Name)
        }

    override fun partiesFromName(query: String, exactMatch: Boolean) = 
        guard(invokeRpc(CordaRPCOps::partiesFromName)) {
            implementation.partiesFromName(query, exactMatch)
        }

    override fun registeredFlows() =
        guard(invokeRpc(CordaRPCOps::registeredFlows),
              implementation::registeredFlows)

    override fun nodeInfoFromParty(party: AbstractParty) =
        guard(invokeRpc(CordaRPCOps::nodeInfoFromParty)) {
            implementation.nodeInfoFromParty(party)
        }

    override fun clearNetworkMapCache() =
        guard(invokeRpc(CordaRPCOps::clearNetworkMapCache),
              implementation::clearNetworkMapCache)

    override fun <T : ContractState> vaultQuery(contractStateType: Class<out T>) =
       guard(invokeRpc("vaultQuery")) {
            implementation.vaultQuery(contractStateType)
       }

    override fun <T : ContractState> vaultQueryByCriteria(criteria: QueryCriteria,
                                                          contractStateType: Class<out T>) =
        guard(invokeRpc("vaultQueryByCriteria")) {
           implementation.vaultQueryByCriteria(criteria, contractStateType)
        }

    override fun <T : ContractState> vaultQueryByWithPagingSpec(contractStateType: Class<out T>,
                                                                criteria: QueryCriteria,
                                                                paging: PageSpecification) =
        guard(invokeRpc("vaultQueryByWithPagingSpec")) {
            implementation.vaultQueryByWithPagingSpec(contractStateType, criteria, paging)
        }

    override fun <T : ContractState> vaultQueryByWithSorting(contractStateType: Class<out T>,
                                                             criteria: QueryCriteria,
                                                             sorting: Sort) =
        guard(invokeRpc("vaultQueryByWithSorting")) {
            implementation.vaultQueryByWithSorting(contractStateType, criteria, sorting)
        }

    override fun <T : ContractState> vaultTrack(contractStateType: Class<out T>) =
        guard(invokeRpc("vaultTrack")) {
           implementation.vaultTrack(contractStateType)
        }

    override fun <T : ContractState> vaultTrackByCriteria(contractStateType: Class<out T>,
                                                          criteria: QueryCriteria) =
        guard(invokeRpc("vaultTrackByCriteria")) {
            implementation.vaultTrackByCriteria(contractStateType, criteria)
        }

    override fun <T : ContractState> vaultTrackByWithPagingSpec(contractStateType: Class<out T>,
                                                                criteria: QueryCriteria,
                                                                paging: PageSpecification) =
        guard(invokeRpc("vaultTrackByWithPagingSpec")) {
            implementation.vaultTrackByWithPagingSpec(contractStateType, criteria, paging)
        }

    override fun <T : ContractState> vaultTrackByWithSorting(contractStateType: Class<out T>,
                                                             criteria: QueryCriteria,
                                                             sorting: Sort) =
        guard(invokeRpc("vaultTrackByWithSorting")) {
            implementation.vaultTrackByWithSorting(contractStateType, criteria, sorting)
        }

    private fun authorise(permission : DomainPermission) {
        if (!rpcContext().authenticatedSubject.isPermitted(permission)) {
            throw PermissionException ("Current user permissions do not authorise $permission")
        }
    }

    private inline fun <T> guard(permission: DomainPermission, action: () -> T) : T{
        authorise(permission)
        return action()
    }
}
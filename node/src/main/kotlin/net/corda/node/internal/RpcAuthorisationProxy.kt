/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal

import net.corda.client.rpc.PermissionException
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.node.services.messaging.RpcAuthContext
import java.io.InputStream
import java.security.PublicKey

// TODO change to KFunction reference after Kotlin fixes https://youtrack.jetbrains.com/issue/KT-12140
class RpcAuthorisationProxy(private val implementation: CordaRPCOps, private val context: () -> RpcAuthContext) : CordaRPCOps {
    override fun networkParametersFeed(): DataFeed<ParametersUpdateInfo?, ParametersUpdateInfo> = guard("networkParametersFeed") {
        implementation.networkParametersFeed()
    }

    override fun acceptNewNetworkParameters(parametersHash: SecureHash) = guard("acceptNewNetworkParameters") {
        implementation.acceptNewNetworkParameters(parametersHash)
    }

    override fun uploadAttachmentWithMetadata(jar: InputStream, uploader: String, filename: String): SecureHash = guard("uploadAttachmentWithMetadata") {
        implementation.uploadAttachmentWithMetadata(jar, uploader, filename)
    }

    override fun queryAttachments(query: AttachmentQueryCriteria, sorting: AttachmentSort?): List<AttachmentId> = guard("queryAttachments") {
        implementation.queryAttachments(query, sorting)
    }

    override fun stateMachinesSnapshot() = guard("stateMachinesSnapshot") {
        implementation.stateMachinesSnapshot()
    }

    override fun stateMachinesFeed() = guard("stateMachinesFeed") {
        implementation.stateMachinesFeed()
    }

    override fun <T : ContractState> vaultQueryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>) = guard("vaultQueryBy") {
        implementation.vaultQueryBy(criteria, paging, sorting, contractStateType)
    }

    override fun <T : ContractState> vaultTrackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>) = guard("vaultTrackBy") {
        implementation.vaultTrackBy(criteria, paging, sorting, contractStateType)
    }

    override fun internalVerifiedTransactionsSnapshot() = guard("internalVerifiedTransactionsSnapshot", implementation::internalVerifiedTransactionsSnapshot)

    override fun internalVerifiedTransactionsFeed() = guard("internalVerifiedTransactionsFeed", implementation::internalVerifiedTransactionsFeed)

    override fun stateMachineRecordedTransactionMappingSnapshot() = guard("stateMachineRecordedTransactionMappingSnapshot", implementation::stateMachineRecordedTransactionMappingSnapshot)

    override fun stateMachineRecordedTransactionMappingFeed() = guard("stateMachineRecordedTransactionMappingFeed", implementation::stateMachineRecordedTransactionMappingFeed)

    override fun networkMapSnapshot(): List<NodeInfo> = guard("networkMapSnapshot", implementation::networkMapSnapshot)

    override fun networkMapFeed(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> = guard("networkMapFeed", implementation::networkMapFeed)

    override fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?) = guard("startFlowDynamic", listOf(logicType)) {
        implementation.startFlowDynamic(logicType, *args)
    }

    override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?) = guard("startTrackedFlowDynamic", listOf(logicType)) {
        implementation.startTrackedFlowDynamic(logicType, *args)
    }

    override fun killFlow(id: StateMachineRunId): Boolean = guard("killFlow") {
        return implementation.killFlow(id)
    }

    override fun nodeInfo(): NodeInfo = guard("nodeInfo", implementation::nodeInfo)

    override fun notaryIdentities(): List<Party> = guard("notaryIdentities", implementation::notaryIdentities)

    override fun addVaultTransactionNote(txnId: SecureHash, txnNote: String) = guard("addVaultTransactionNote") {
        implementation.addVaultTransactionNote(txnId, txnNote)
    }

    override fun getVaultTransactionNotes(txnId: SecureHash): Iterable<String> = guard("getVaultTransactionNotes") {
        implementation.getVaultTransactionNotes(txnId)
    }

    override fun attachmentExists(id: SecureHash) = guard("attachmentExists") {
        implementation.attachmentExists(id)
    }

    override fun openAttachment(id: SecureHash) = guard("openAttachment") {
        implementation.openAttachment(id)
    }

    override fun uploadAttachment(jar: InputStream) = guard("uploadAttachment") {
        implementation.uploadAttachment(jar)
    }

    override fun currentNodeTime() = guard("currentNodeTime", implementation::currentNodeTime)

    override fun waitUntilNetworkReady() = guard("waitUntilNetworkReady", implementation::waitUntilNetworkReady)

    override fun wellKnownPartyFromAnonymous(party: AbstractParty) = guard("wellKnownPartyFromAnonymous") {
        implementation.wellKnownPartyFromAnonymous(party)
    }

    override fun partyFromKey(key: PublicKey) = guard("partyFromKey") {
        implementation.partyFromKey(key)
    }

    override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name) = guard("wellKnownPartyFromX500Name") {
        implementation.wellKnownPartyFromX500Name(x500Name)
    }

    override fun notaryPartyFromX500Name(x500Name: CordaX500Name) = guard("notaryPartyFromX500Name") {
        implementation.notaryPartyFromX500Name(x500Name)
    }

    override fun partiesFromName(query: String, exactMatch: Boolean) = guard("partiesFromName") {
        implementation.partiesFromName(query, exactMatch)
    }

    override fun registeredFlows() = guard("registeredFlows", implementation::registeredFlows)

    override fun nodeInfoFromParty(party: AbstractParty) = guard("nodeInfoFromParty") {
        implementation.nodeInfoFromParty(party)
    }

    override fun clearNetworkMapCache() = guard("clearNetworkMapCache", implementation::clearNetworkMapCache)

    override fun <T : ContractState> vaultQuery(contractStateType: Class<out T>): Vault.Page<T> = guard("vaultQuery") {
        implementation.vaultQuery(contractStateType)
    }

    override fun <T : ContractState> vaultQueryByCriteria(criteria: QueryCriteria, contractStateType: Class<out T>): Vault.Page<T> = guard("vaultQueryByCriteria") {
        implementation.vaultQueryByCriteria(criteria, contractStateType)
    }

    override fun <T : ContractState> vaultQueryByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): Vault.Page<T> = guard("vaultQueryByWithPagingSpec") {
        implementation.vaultQueryByWithPagingSpec(contractStateType, criteria, paging)
    }

    override fun <T : ContractState> vaultQueryByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort): Vault.Page<T> = guard("vaultQueryByWithSorting") {
        implementation.vaultQueryByWithSorting(contractStateType, criteria, sorting)
    }

    override fun <T : ContractState> vaultTrack(contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> = guard("vaultTrack") {
        implementation.vaultTrack(contractStateType)
    }

    override fun <T : ContractState> vaultTrackByCriteria(contractStateType: Class<out T>, criteria: QueryCriteria): DataFeed<Vault.Page<T>, Vault.Update<T>> = guard("vaultTrackByCriteria") {
        implementation.vaultTrackByCriteria(contractStateType, criteria)
    }

    override fun <T : ContractState> vaultTrackByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): DataFeed<Vault.Page<T>, Vault.Update<T>> = guard("vaultTrackByWithPagingSpec") {
        implementation.vaultTrackByWithPagingSpec(contractStateType, criteria, paging)
    }

    override fun <T : ContractState> vaultTrackByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update<T>> = guard("vaultTrackByWithSorting") {
        implementation.vaultTrackByWithSorting(contractStateType, criteria, sorting)
    }

    override fun setFlowsDrainingModeEnabled(enabled: Boolean) = guard("setFlowsDrainingModeEnabled") {
        implementation.setFlowsDrainingModeEnabled(enabled)
    }

    override fun isFlowsDrainingModeEnabled(): Boolean = guard("isFlowsDrainingModeEnabled", implementation::isFlowsDrainingModeEnabled)

    override fun shutdown() = guard("shutdown", implementation::shutdown)

    // TODO change to KFunction reference after Kotlin fixes https://youtrack.jetbrains.com/issue/KT-12140
    private inline fun <RESULT> guard(methodName: String, action: () -> RESULT) = guard(methodName, emptyList(), action)

    // TODO change to KFunction reference after Kotlin fixes https://youtrack.jetbrains.com/issue/KT-12140
    private inline fun <RESULT> guard(methodName: String, args: List<Class<*>>, action: () -> RESULT) : RESULT {
        if (!context().isPermitted(methodName, *(args.map { it.name }.toTypedArray()))) {
            throw PermissionException("User not authorized to perform RPC call $methodName with target $args")
        }
        else {
            return action()
        }
    }
}
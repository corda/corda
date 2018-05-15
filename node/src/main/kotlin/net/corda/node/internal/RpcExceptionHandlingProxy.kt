package net.corda.node.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.doOnError
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.doOnError
import net.corda.core.internal.concurrent.mapError
import net.corda.core.mapErrors
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.exceptions.InternalNodeException
import net.corda.nodeapi.exceptions.adapters.InternalObfuscatingFlowHandle
import net.corda.nodeapi.exceptions.adapters.InternalObfuscatingFlowProgressHandle
import java.io.InputStream
import java.security.PublicKey

class RpcExceptionHandlingProxy(private val delegate: SecureCordaRPCOps) : CordaRPCOps {

    private companion object {
        private val logger = loggerFor<RpcExceptionHandlingProxy>()
    }

    override val protocolVersion: Int get() = delegate.protocolVersion

    override fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T> = wrap {

        val handle = delegate.startFlowDynamic(logicType, *args)
        val result = InternalObfuscatingFlowHandle(handle)
        result.returnValue.doOnError { error -> logger.error(error.message, error) }
        result
    }

    override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> = wrap {

        val handle = delegate.startTrackedFlowDynamic(logicType, *args)
        val result = InternalObfuscatingFlowProgressHandle(handle)
        result.returnValue.doOnError { error -> logger.error(error.message, error) }
        result
    }

    override fun waitUntilNetworkReady() = wrapFuture(delegate::waitUntilNetworkReady)

    override fun stateMachinesFeed() = wrapFeed(delegate::stateMachinesFeed)

    override fun <T : ContractState> vaultTrackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>) = wrapFeed { delegate.vaultTrackBy(criteria, paging, sorting, contractStateType) }

    override fun <T : ContractState> vaultTrack(contractStateType: Class<out T>) = wrapFeed { delegate.vaultTrack(contractStateType) }

    override fun <T : ContractState> vaultTrackByCriteria(contractStateType: Class<out T>, criteria: QueryCriteria) = wrapFeed { delegate.vaultTrackByCriteria(contractStateType, criteria) }

    override fun <T : ContractState> vaultTrackByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification) = wrapFeed { delegate.vaultTrackByWithPagingSpec(contractStateType, criteria, paging) }

    override fun <T : ContractState> vaultTrackByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort) = wrapFeed { delegate.vaultTrackByWithSorting(contractStateType, criteria, sorting) }

    override fun stateMachineRecordedTransactionMappingFeed() = wrapFeed(delegate::stateMachineRecordedTransactionMappingFeed)

    override fun networkMapFeed() = wrapFeed(delegate::networkMapFeed)

    override fun networkParametersFeed() = wrapFeed(delegate::networkParametersFeed)

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun internalVerifiedTransactionsFeed() = wrapFeed(delegate::internalVerifiedTransactionsFeed)

    override fun stateMachinesSnapshot() = wrap(delegate::stateMachinesSnapshot)

    override fun <T : ContractState> vaultQueryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>) = wrap { delegate.vaultQueryBy(criteria, paging, sorting, contractStateType) }

    override fun <T : ContractState> vaultQuery(contractStateType: Class<out T>) = wrap { delegate.vaultQuery(contractStateType) }

    override fun <T : ContractState> vaultQueryByCriteria(criteria: QueryCriteria, contractStateType: Class<out T>) = wrap { delegate.vaultQueryByCriteria(criteria, contractStateType) }

    override fun <T : ContractState> vaultQueryByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification) = wrap { delegate.vaultQueryByWithPagingSpec(contractStateType, criteria, paging) }

    override fun <T : ContractState> vaultQueryByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort) = wrap { delegate.vaultQueryByWithSorting(contractStateType, criteria, sorting) }

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun internalVerifiedTransactionsSnapshot() = wrap(delegate::internalVerifiedTransactionsSnapshot)

    override fun stateMachineRecordedTransactionMappingSnapshot() = wrap(delegate::stateMachineRecordedTransactionMappingSnapshot)

    override fun networkMapSnapshot() = wrap(delegate::networkMapSnapshot)

    override fun acceptNewNetworkParameters(parametersHash: SecureHash) = wrap { delegate.acceptNewNetworkParameters(parametersHash) }

    override fun nodeInfo() = wrap(delegate::nodeInfo)

    override fun notaryIdentities() = wrap(delegate::notaryIdentities)

    override fun addVaultTransactionNote(txnId: SecureHash, txnNote: String) = wrap { delegate.addVaultTransactionNote(txnId, txnNote) }

    override fun getVaultTransactionNotes(txnId: SecureHash) = wrap { delegate.getVaultTransactionNotes(txnId) }

    override fun attachmentExists(id: SecureHash) = wrap { delegate.attachmentExists(id) }

    override fun openAttachment(id: SecureHash) = wrap { delegate.openAttachment(id) }

    override fun uploadAttachment(jar: InputStream) = wrap { delegate.uploadAttachment(jar) }

    override fun uploadAttachmentWithMetadata(jar: InputStream, uploader: String, filename: String) = wrap { delegate.uploadAttachmentWithMetadata(jar, uploader, filename) }

    override fun queryAttachments(query: AttachmentQueryCriteria, sorting: AttachmentSort?) = wrap { delegate.queryAttachments(query, sorting) }

    override fun currentNodeTime() = wrap(delegate::currentNodeTime)

    override fun wellKnownPartyFromAnonymous(party: AbstractParty) = wrap { delegate.wellKnownPartyFromAnonymous(party) }

    override fun partyFromKey(key: PublicKey) = wrap { delegate.partyFromKey(key) }

    override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name) = wrap { delegate.wellKnownPartyFromX500Name(x500Name) }

    override fun notaryPartyFromX500Name(x500Name: CordaX500Name) = wrap { delegate.notaryPartyFromX500Name(x500Name) }

    override fun partiesFromName(query: String, exactMatch: Boolean) = wrap { delegate.partiesFromName(query, exactMatch) }

    override fun registeredFlows() = wrap(delegate::registeredFlows)

    override fun nodeInfoFromParty(party: AbstractParty) = wrap { delegate.nodeInfoFromParty(party) }

    override fun clearNetworkMapCache() = wrap(delegate::clearNetworkMapCache)

    override fun setFlowsDrainingModeEnabled(enabled: Boolean) = wrap { delegate.setFlowsDrainingModeEnabled(enabled) }

    override fun isFlowsDrainingModeEnabled() = wrap(delegate::isFlowsDrainingModeEnabled)

    override fun shutdown() = wrap(delegate::shutdown)

    private fun <RESULT> wrap(call: () -> RESULT): RESULT {

        return try {
            call.invoke()
        } catch (error: Throwable) {
            logger.error(error.message, error)
            throw InternalNodeException.obfuscate(error)
        }
    }

    private fun <SNAPSHOT, ELEMENT> wrapFeed(call: () -> DataFeed<SNAPSHOT, ELEMENT>) = wrap {

        call.invoke().doOnError { error -> logger.error(error.message, error) }.mapErrors(InternalNodeException.Companion::obfuscate)
    }

    private fun <RESULT> wrapFuture(call: () -> CordaFuture<RESULT>): CordaFuture<RESULT> = wrap { call.invoke().mapError(InternalNodeException.Companion::obfuscate).doOnError { error -> logger.error(error.message, error) } }
}
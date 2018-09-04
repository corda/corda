package net.corda.rpcWorker

import com.google.common.util.concurrent.SettableFuture
import net.corda.client.rpc.notUsed
import net.corda.core.CordaRuntimeException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.*
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.RPC_UPLOADER
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.sign
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.flowworker.*
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.services.vault.NodeVaultService
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import java.io.InputStream
import java.net.ConnectException
import java.security.PublicKey
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CordaRpcWorkerOps(
        private val services: ServiceHubInternal,
        private val shutdownNode: () -> Unit
) : CordaRPCOps {

    companion object {
        const val RPC_WORKER_QUEUE_ADDRESS_PREFIX = "${ArtemisMessagingComponent.INTERNAL_PREFIX}rpc.worker."
    }

    private val flowWorkerQueueAddress = "${FlowWorker.FLOW_WORKER_QUEUE_ADDRESS_PREFIX}${services.myInfo.legalIdentities[0].owningKey.toStringShort()}"

    private val rpcWorkerQueueAddress = "$RPC_WORKER_QUEUE_ADDRESS_PREFIX${services.myInfo.legalIdentities[0].owningKey.toStringShort()}"
    private val rpcWorkerId = UUID.randomUUID().toString()
    private val rpcWorkerQueueName = "$rpcWorkerQueueAddress.$rpcWorkerId"

    private val artemisClient = ArtemisMessagingClient(services.configuration.p2pSslOptions, services.configuration.messagingServerAddress!!, services.networkParameters.maxMessageSize)
    private lateinit var session: ClientSession
    private lateinit var producer: ClientProducer

    private val flowReplyStateMachineRunIdMap = ConcurrentHashMap<Trace.InvocationId, SettableFuture<StateMachineRunId>>()
    private val flowReplyResultMap = ConcurrentHashMap<Trace.InvocationId, OpenFuture<Any?>>()

    fun start() {
        session = artemisClient.start().session
        producer = session.createProducer()

        val rpcWorkerQueueQuery = session.queueQuery(SimpleString(rpcWorkerQueueName))
        if (!rpcWorkerQueueQuery.isExists) {
            session.createQueue(rpcWorkerQueueAddress, RoutingType.ANYCAST, rpcWorkerQueueName, true)
        }

        val consumer = session.createConsumer(rpcWorkerQueueName)
        consumer.setMessageHandler { message -> handleFlowWorkerMessage(message) }

        networkMapFeed().updates.subscribe { mapChange: NetworkMapCache.MapChange? ->
            val networkMapUpdateMessage = NetworkMapUpdate(services.myInfo.legalIdentities.first().name, mapChange!!)
            val artemisMessage = session.createMessage(true)
            artemisMessage.writeBodyBufferBytes(networkMapUpdateMessage.serialize(context = SerializationDefaults.RPC_CLIENT_CONTEXT).bytes)
            producer.send(flowWorkerQueueAddress, artemisMessage)
        }
    }

    private fun handleFlowWorkerMessage(message: ClientMessage) {
        val data = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }
        val flowWorkerMessage = data.deserialize<FlowWorkerMessage>(context = SerializationDefaults.RPC_CLIENT_CONTEXT)

        when (flowWorkerMessage) {
            is FlowReplyStateMachineRunId -> {
                flowReplyStateMachineRunIdMap.remove(flowWorkerMessage.replyId)?.set(flowWorkerMessage.id)
            }
            is FlowReplyResult -> {
                flowReplyResultMap.remove(flowWorkerMessage.replyId)?.set(flowWorkerMessage.result)
                // TODO hack, fix the way we populate contractStateTypeMappings
                (services.vaultService as NodeVaultService).bootstrapContractStateTypes()
            }
        }
    }

    override fun networkMapSnapshot(): List<NodeInfo> {
        val (snapshot, updates) = networkMapFeed()
        updates.notUsed()
        return snapshot
    }

    override fun networkParametersFeed(): DataFeed<ParametersUpdateInfo?, ParametersUpdateInfo> {
        return services.networkMapUpdater.trackParametersUpdate()
    }

    override fun acceptNewNetworkParameters(parametersHash: SecureHash) {
        services.networkMapUpdater.acceptNewNetworkParameters(
                parametersHash,
                // TODO When multiple identities design will be better specified this should be signature from node operator.
                { hash -> hash.serialize().sign { services.keyManagementService.sign(it.bytes, services.myInfo.legalIdentities[0].owningKey) } }
        )
    }

    override fun networkMapFeed(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> {
        return services.networkMapCache.track()
    }

    override fun <T : ContractState> vaultQueryBy(criteria: QueryCriteria,
                                                  paging: PageSpecification,
                                                  sorting: Sort,
                                                  contractStateType: Class<out T>): Vault.Page<T> {
        contractStateType.checkIsA<ContractState>()
        return services.vaultService._queryBy(criteria, paging, sorting, contractStateType)
    }

    @RPCReturnsObservables
    override fun <T : ContractState> vaultTrackBy(criteria: QueryCriteria,
                                                  paging: PageSpecification,
                                                  sorting: Sort,
                                                  contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        contractStateType.checkIsA<ContractState>()
        return services.vaultService._trackBy(criteria, paging, sorting, contractStateType)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun internalVerifiedTransactionsSnapshot(): List<SignedTransaction> {
        val (snapshot, updates) = @Suppress("DEPRECATION") internalVerifiedTransactionsFeed()
        updates.notUsed()
        return snapshot
    }

    override fun internalFindVerifiedTransaction(txnId: SecureHash): SignedTransaction? = services.validatedTransactions.getTransaction(txnId)

    @Suppress("OverridingDeprecatedMember")
    override fun internalVerifiedTransactionsFeed(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return services.validatedTransactions.track()
    }

    override fun stateMachinesSnapshot(): List<StateMachineInfo> {
        val (snapshot, updates) = stateMachinesFeed()
        updates.notUsed()
        return snapshot
    }

    override fun killFlow(id: StateMachineRunId): Boolean {
        TODO()
    }

    override fun stateMachinesFeed(): DataFeed<List<StateMachineInfo>, StateMachineUpdate> {
        TODO()
    }

    override fun stateMachineRecordedTransactionMappingSnapshot(): List<StateMachineTransactionMapping> {
        val (snapshot, updates) = stateMachineRecordedTransactionMappingFeed()
        updates.notUsed()
        return snapshot
    }

    override fun stateMachineRecordedTransactionMappingFeed(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> {
        return services.stateMachineRecordedTransactionMapping.track()
    }

    override fun nodeInfo(): NodeInfo {
        return services.myInfo
    }

    override fun notaryIdentities(): List<Party> {
        return services.networkMapCache.notaryIdentities
    }

    override fun addVaultTransactionNote(txnId: SecureHash, txnNote: String) {
        services.vaultService.addNoteToTransaction(txnId, txnNote)
    }

    override fun getVaultTransactionNotes(txnId: SecureHash): Iterable<String> {
        return services.vaultService.getTransactionNotes(txnId)
    }

    override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> {
        TODO()
    }

    override fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T> {
        // TODO
        val context = InvocationContext.rpc(Actor(Actor.Id("Mark"), AuthServiceId("Test"), CordaX500Name("ff", "ff", "GB")))

        val replyId = Trace.InvocationId.newInstance()

        val startFlowMessage = StartFlow(services.myInfo.legalIdentities.first().name, logicType, args, context, rpcWorkerQueueAddress, replyId)
        val artemisMessage = session.createMessage(true)
        artemisMessage.writeBodyBufferBytes(startFlowMessage.serialize(context = SerializationDefaults.RPC_CLIENT_CONTEXT).bytes)
        producer.send(flowWorkerQueueAddress, artemisMessage)

        val flowReplyStateMachineRunIdFuture = SettableFuture.create<StateMachineRunId>()
        flowReplyStateMachineRunIdMap[replyId] = flowReplyStateMachineRunIdFuture

        val flowReplyResultFuture = openFuture<T>()
        flowReplyResultMap[replyId] = uncheckedCast(flowReplyResultFuture)

        return FlowHandleImpl(flowReplyStateMachineRunIdFuture.get(), flowReplyResultFuture)
    }

    override fun attachmentExists(id: SecureHash): Boolean {
        return services.attachments.openAttachment(id) != null
    }

    override fun openAttachment(id: SecureHash): InputStream {
        return services.attachments.openAttachment(id)!!.open()
    }

    override fun uploadAttachment(jar: InputStream): SecureHash {
        return services.attachments.importAttachment(jar, RPC_UPLOADER, null)
    }

    override fun uploadAttachmentWithMetadata(jar: InputStream, uploader: String, filename: String): SecureHash {
        return services.attachments.importAttachment(jar, uploader, filename)
    }

    override fun queryAttachments(query: AttachmentQueryCriteria, sorting: AttachmentSort?): List<AttachmentId> {
        return services.attachments.queryAttachments(query, sorting)
    }

    override fun currentNodeTime(): Instant = Instant.now(services.clock)

    override fun waitUntilNetworkReady(): CordaFuture<Void?> = services.networkMapCache.nodeReady

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        return services.identityService.wellKnownPartyFromAnonymous(party)
    }

    override fun partyFromKey(key: PublicKey): Party? {
        return services.identityService.partyFromKey(key)
    }

    override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name): Party? {
        return services.identityService.wellKnownPartyFromX500Name(x500Name)
    }

    override fun notaryPartyFromX500Name(x500Name: CordaX500Name): Party? = services.networkMapCache.getNotary(x500Name)

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        return services.identityService.partiesFromName(query, exactMatch)
    }

    override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? {
        return services.networkMapCache.getNodeByLegalIdentity(party)
    }

    override fun registeredFlows(): List<String> = services.rpcFlows.map { it.name }.sorted()

    override fun clearNetworkMapCache() {
        services.networkMapCache.clearNetworkMapCache()
    }

    override fun refreshNetworkMapCache() {
        try {
            services.networkMapUpdater.updateNetworkMapCache()
        } catch (e: Exception) {
            when (e) {
                is ConnectException -> throw CordaRuntimeException("There is connection problem to network map. The possible causes are incorrect configuration or network map service being down")
                else -> throw e
            }
        }
    }

    override fun <T : ContractState> vaultQuery(contractStateType: Class<out T>): Vault.Page<T> {
        return vaultQueryBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), contractStateType)
    }

    override fun <T : ContractState> vaultQueryByCriteria(criteria: QueryCriteria, contractStateType: Class<out T>): Vault.Page<T> {
        return vaultQueryBy(criteria, PageSpecification(), Sort(emptySet()), contractStateType)
    }

    override fun <T : ContractState> vaultQueryByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): Vault.Page<T> {
        return vaultQueryBy(criteria, paging, Sort(emptySet()), contractStateType)
    }

    override fun <T : ContractState> vaultQueryByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort): Vault.Page<T> {
        return vaultQueryBy(criteria, PageSpecification(), sorting, contractStateType)
    }

    override fun <T : ContractState> vaultTrack(contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return vaultTrackBy(QueryCriteria.VaultQueryCriteria(), PageSpecification(), Sort(emptySet()), contractStateType)
    }

    override fun <T : ContractState> vaultTrackByCriteria(contractStateType: Class<out T>, criteria: QueryCriteria): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return vaultTrackBy(criteria, PageSpecification(), Sort(emptySet()), contractStateType)
    }

    override fun <T : ContractState> vaultTrackByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return vaultTrackBy(criteria, paging, Sort(emptySet()), contractStateType)
    }

    override fun <T : ContractState> vaultTrackByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        return vaultTrackBy(criteria, PageSpecification(), sorting, contractStateType)
    }

    override fun setFlowsDrainingModeEnabled(enabled: Boolean) {
        services.nodeProperties.flowsDrainingMode.setEnabled(enabled)
    }

    override fun isFlowsDrainingModeEnabled(): Boolean {
        return services.nodeProperties.flowsDrainingMode.isEnabled()
    }

    override fun shutdown() {
        artemisClient.stop()
        shutdownNode.invoke()
    }

    private fun stateMachineInfoFromFlowLogic(flowLogic: FlowLogic<*>): StateMachineInfo {
        return StateMachineInfo(flowLogic.runId, flowLogic.javaClass.name, flowLogic.stateMachine.context.toFlowInitiator(), flowLogic.track(), flowLogic.stateMachine.context)
    }

    private fun stateMachineUpdateFromStateMachineChange(change: StateMachineManager.Change): StateMachineUpdate {
        return when (change) {
            is StateMachineManager.Change.Add -> StateMachineUpdate.Added(stateMachineInfoFromFlowLogic(change.logic))
            is StateMachineManager.Change.Removed -> StateMachineUpdate.Removed(change.logic.runId, change.result)
        }
    }

    private fun InvocationContext.toFlowInitiator(): FlowInitiator {
        val principal = origin.principal().name
        return when (origin) {
            is InvocationOrigin.RPC -> FlowInitiator.RPC(principal)
            is InvocationOrigin.Peer -> {
                val wellKnownParty = services.identityService.wellKnownPartyFromX500Name((origin as InvocationOrigin.Peer).party)
                wellKnownParty?.let { FlowInitiator.Peer(it) }
                        ?: throw IllegalStateException("Unknown peer with name ${(origin as InvocationOrigin.Peer).party}.")
            }
            is InvocationOrigin.Service -> FlowInitiator.Service(principal)
            InvocationOrigin.Shell -> FlowInitiator.Shell
            is InvocationOrigin.Scheduled -> FlowInitiator.Scheduled((origin as InvocationOrigin.Scheduled).scheduledState)
        }
    }

    /**
     * RPC can be invoked from the shell where the type parameter of any [Class] parameter is lost, so we must
     * explicitly check that the provided [Class] is the one we want.
     */
    private inline fun <reified TARGET> Class<*>.checkIsA() {
        require(TARGET::class.java.isAssignableFrom(this)) { "$name is not a ${TARGET::class.java.name}" }
    }
}
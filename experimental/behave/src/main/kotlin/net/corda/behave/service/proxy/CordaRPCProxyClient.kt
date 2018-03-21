package net.corda.behave.service.proxy

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.responseAs
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import java.io.InputStream
import java.security.PublicKey
import java.time.Instant

class CordaRPCProxyClient(private val targetHostAndPort: NetworkHostAndPort) : CordaRPCOps {

    override fun stateMachinesSnapshot(): List<StateMachineInfo> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stateMachinesFeed(): DataFeed<List<StateMachineInfo>, StateMachineUpdate> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultQueryBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): Vault.Page<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultQuery(contractStateType: Class<out T>): Vault.Page<T> {
        return  doPost(targetHostAndPort, "vault-query", contractStateType.name.serialize().bytes)
    }

    override fun <T : ContractState> vaultQueryByCriteria(criteria: QueryCriteria, contractStateType: Class<out T>): Vault.Page<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultQueryByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): Vault.Page<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultQueryByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort): Vault.Page<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultTrackBy(criteria: QueryCriteria, paging: PageSpecification, sorting: Sort, contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultTrack(contractStateType: Class<out T>): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultTrackByCriteria(contractStateType: Class<out T>, criteria: QueryCriteria): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultTrackByWithPagingSpec(contractStateType: Class<out T>, criteria: QueryCriteria, paging: PageSpecification): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : ContractState> vaultTrackByWithSorting(contractStateType: Class<out T>, criteria: QueryCriteria, sorting: Sort): DataFeed<Vault.Page<T>, Vault.Update<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun internalVerifiedTransactionsSnapshot(): List<SignedTransaction> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun internalVerifiedTransactionsFeed(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stateMachineRecordedTransactionMappingSnapshot(): List<StateMachineTransactionMapping> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stateMachineRecordedTransactionMappingFeed(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun networkMapSnapshot(): List<NodeInfo> {
        return doGet(targetHostAndPort, "network-map-snapshot")
    }

    override fun networkMapFeed(): DataFeed<List<NodeInfo>, NetworkMapCache.MapChange> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun networkParametersFeed(): DataFeed<ParametersUpdateInfo?, ParametersUpdateInfo> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptNewNetworkParameters(parametersHash: SecureHash) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T> startFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowHandle<T> {
        val flowName = logicType.name
        val argList = listOf(flowName, *args)

        println("Corda RPC Proxy client calling: $flowName with values: $argList")
        val response = doPost<Any>(targetHostAndPort, "start-flow", argList.serialize().bytes)
        val result = doneFuture(response)
        return FlowHandleImpl(StateMachineRunId.createRandom(), result) as FlowHandle<T>
    }

    override fun <T> startTrackedFlowDynamic(logicType: Class<out FlowLogic<T>>, vararg args: Any?): FlowProgressHandle<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun nodeInfo(): NodeInfo {
        return doGet(targetHostAndPort, "node-info")
    }

    override fun notaryIdentities(): List<Party> {
        return doGet(targetHostAndPort, "notary-identities")
    }

    override fun addVaultTransactionNote(txnId: SecureHash, txnNote: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getVaultTransactionNotes(txnId: SecureHash): Iterable<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun attachmentExists(id: SecureHash): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun openAttachment(id: SecureHash): InputStream {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun uploadAttachment(jar: InputStream): SecureHash {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun uploadAttachmentWithMetadata(jar: InputStream, uploader: String, filename: String): SecureHash {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun queryAttachments(query: AttachmentQueryCriteria, sorting: AttachmentSort?): List<AttachmentId> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun currentNodeTime(): Instant {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun waitUntilNetworkReady(): CordaFuture<Void?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun partyFromKey(key: PublicKey): Party? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun wellKnownPartyFromX500Name(x500Name: CordaX500Name): Party? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun notaryPartyFromX500Name(x500Name: CordaX500Name): Party? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        return doPost(targetHostAndPort, "parties-from-name", query.serialize().bytes)
    }

    override fun registeredFlows(): List<String> {
        return doGet(targetHostAndPort, "registered-flows")
    }

    override fun nodeInfoFromParty(party: AbstractParty): NodeInfo? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun clearNetworkMapCache() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setFlowsDrainingModeEnabled(enabled: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isFlowsDrainingModeEnabled(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun shutdown() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private inline fun <reified T : Any> doPost(hostAndPort: NetworkHostAndPort, path: String, payload: ByteArray) : T {
        val url = java.net.URL("http://$hostAndPort/rpc/$path")
        val connection = url.openHttpConnection().apply {
            doOutput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM)
            outputStream.write(payload)
        }
        return connection.responseAs()
    }

    private inline fun <reified T : Any> doGet(hostAndPort: NetworkHostAndPort, path: String): T {
        return java.net.URL("http://$hostAndPort/rpc/$path").openHttpConnection().responseAs()
    }
}
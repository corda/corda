package net.corda.node.services.transactions

import bftsmart.communication.ServerCommunicationSystem
import bftsmart.communication.client.netty.NettyClientServerCommunicationSystemClientSide
import bftsmart.communication.client.netty.NettyClientServerSession
import bftsmart.tom.MessageContext
import bftsmart.tom.ServiceProxy
import bftsmart.tom.ServiceReplica
import bftsmart.tom.core.TOMLayer
import bftsmart.tom.core.messages.TOMMessage
import bftsmart.tom.server.defaultservices.DefaultRecoverable
import bftsmart.tom.server.defaultservices.DefaultReplier
import bftsmart.tom.util.Extractor
import net.corda.core.DeclaredField.Companion.declaredField
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sign
import net.corda.core.identity.Party
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.toTypedArray
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.BFTSMaRt.Client
import net.corda.node.services.transactions.BFTSMaRt.Replica
import net.corda.node.utilities.JDBCHashMap
import net.corda.node.utilities.transaction
import java.nio.file.Path
import java.util.*

/**
 * Implements a replicated transaction commit log based on the [BFT-SMaRt](https://github.com/bft-smart/library)
 * consensus algorithm. Every replica in the cluster is running a [Replica] maintaining the state, and a [Client] is used
 * to relay state modification requests to all [Replica]s.
 */
// TODO: Define and document the configuration of the bft-smart cluster.
// TODO: Potentially update the bft-smart API for our use case or rebuild client and server from lower level building
//       blocks bft-smart provides.
// TODO: Support cluster membership changes. This requires reading about reconfiguration of bft-smart clusters and
//       perhaps a design doc. In general, it seems possible to use the state machine to reconfigure the cluster (reaching
//       consensus about  membership changes). Nodes that join the cluster for the first time or re-join can go through
//       a "recovering" state and request missing data from their peers.
object BFTSMaRt {
    /** Sent from [Client] to [Replica]. */
    @CordaSerializable
    data class CommitRequest(val tx: Any, val callerIdentity: Party)

    /** Sent from [Replica] to [Client]. */
    @CordaSerializable
    sealed class ReplicaResponse {
        data class Error(val error: NotaryError) : ReplicaResponse()
        data class Signature(val txSignature: DigitalSignature) : ReplicaResponse()
    }

    /** An aggregate response from all replica ([Replica]) replies sent from [Client] back to the calling application. */
    @CordaSerializable
    sealed class ClusterResponse {
        data class Error(val error: NotaryError) : ClusterResponse()
        data class Signatures(val txSignatures: List<DigitalSignature>) : ClusterResponse()
    }

    class Client(config: BFTSMaRtConfig, private val clientId: Int) : SingletonSerializeAsToken() {
        companion object {
            private val log = loggerFor<Client>()
        }

        /** A proxy for communicating with the BFT cluster */
        private val proxy = ServiceProxy(clientId, config.path.toString(), buildResponseComparator(), buildExtractor())
        private val sessionTable = (proxy.communicationSystem as NettyClientServerCommunicationSystemClientSide).declaredField<Map<Int, NettyClientServerSession>>("sessionTable").value

        fun dispose() {
            proxy.close() // XXX: Does this do enough?
        }

        private fun awaitClientConnectionToCluster() {
            // TODO: Hopefully we only need to wait for the client's initial connection to the cluster, and this method can be moved to some startup code.
            while (true) {
                val inactive = sessionTable.entries.mapNotNull { if (it.value.channel.isActive) null else it.key }
                if (inactive.isEmpty()) break
                log.info("Client-replica channels not yet active: $clientId to $inactive")
                Thread.sleep((inactive.size * 100).toLong())
            }
        }

        /**
         * Sends a transaction commit request to the BFT cluster. The [proxy] will deliver the request to every
         * replica, and block until a sufficient number of replies are received.
         */
        fun commitTransaction(transaction: Any, otherSide: Party): ClusterResponse {
            require(transaction is FilteredTransaction || transaction is SignedTransaction) { "Unsupported transaction type: ${transaction.javaClass.name}" }
            awaitClientConnectionToCluster()
            val requestBytes = CommitRequest(transaction, otherSide).serialize().bytes
            val responseBytes = proxy.invokeOrdered(requestBytes)
            return responseBytes.deserialize<ClusterResponse>()
        }

        /** A comparator to check if replies from two replicas are the same. */
        private fun buildResponseComparator(): Comparator<ByteArray> {
            return Comparator { o1, o2 ->
                val reply1 = o1.deserialize<ReplicaResponse>()
                val reply2 = o2.deserialize<ReplicaResponse>()
                if (reply1 is ReplicaResponse.Error && reply2 is ReplicaResponse.Error) {
                    // TODO: for now we treat all errors as equal, compare by error type as well
                    0
                } else if (reply1 is ReplicaResponse.Signature && reply2 is ReplicaResponse.Signature) 0 else -1
            }
        }

        /** An extractor to build the final response message for the client application from all received replica replies. */
        private fun buildExtractor(): Extractor {
            return Extractor { replies, _, lastReceived ->
                val responses = replies.mapNotNull { it?.content?.deserialize<ReplicaResponse>() }
                val accepted = responses.filterIsInstance<ReplicaResponse.Signature>()
                val rejected = responses.filterIsInstance<ReplicaResponse.Error>()

                log.debug { "BFT Client $clientId: number of replicas accepted the commit: ${accepted.size}, rejected: ${rejected.size}" }

                // TODO: only return an aggregate if the majority of signatures are replies
                // TODO: return an error reported by the majority and not just the first one
                val aggregateResponse = if (accepted.isNotEmpty()) {
                    log.debug { "Cluster response - signatures: ${accepted.map { it.txSignature }}" }
                    ClusterResponse.Signatures(accepted.map { it.txSignature })
                } else {
                    log.debug { "Cluster response - error: ${rejected.first().error}" }
                    ClusterResponse.Error(rejected.first().error)
                }

                val messageContent = aggregateResponse.serialize().bytes
                // TODO: is it safe use the last message for sender/session/sequence info
                val reply = replies[lastReceived]
                TOMMessage(reply.sender, reply.session, reply.sequence, messageContent, reply.viewID)
            }
        }
    }

    /** ServiceReplica doesn't have any kind of shutdown method, so we add one in this subclass. */
    private class CordaServiceReplica(replicaId: Int, configHome: Path, owner: DefaultRecoverable) : ServiceReplica(replicaId, configHome.toString(), owner, owner, null, DefaultReplier()) {
        private val tomLayerField = declaredField<TOMLayer>(ServiceReplica::class, "tomLayer")
        private val csField = declaredField<ServerCommunicationSystem>(ServiceReplica::class, "cs")
        fun dispose() {
            // Half of what restart does:
            val tomLayer = tomLayerField.value
            tomLayer.shutdown() // Non-blocking.
            val cs = csField.value
            cs.join()
            cs.serversConn.join()
            tomLayer.join()
            tomLayer.deliveryThread.join()
            // TODO: At the cluster level, join all Sender/Receiver threads.
        }
    }

    /**
     * Maintains the commit log and executes commit commands received from the [Client].
     *
     * The validation logic can be specified by implementing the [executeCommand] method.
     */
    abstract class Replica(config: BFTSMaRtConfig,
                           replicaId: Int,
                           tableName: String,
                           private val services: ServiceHubInternal,
                           private val timeWindowChecker: TimeWindowChecker) : DefaultRecoverable() {
        companion object {
            private val log = loggerFor<Replica>()
        }

        // TODO: Use Requery with proper DB schema instead of JDBCHashMap.
        // Must be initialised before ServiceReplica is started
        private val commitLog = services.database.transaction { JDBCHashMap<StateRef, UniquenessProvider.ConsumingTx>(tableName) }
        @Suppress("LeakingThis")
        private val replica = CordaServiceReplica(replicaId, config.path, this)

        fun dispose() {
            replica.dispose()
        }

        override fun appExecuteUnordered(command: ByteArray, msgCtx: MessageContext): ByteArray? {
            throw NotImplementedError("No unordered operations supported")
        }

        override fun appExecuteBatch(command: Array<ByteArray>, mcs: Array<MessageContext>): Array<ByteArray?> {
            return Arrays.stream(command).map(this::executeCommand).toTypedArray()
        }

        /**
         * Implement logic to execute the command and commit the transaction to the log.
         * Helper methods are provided for transaction processing: [commitInputStates], [validateTimeWindow], and [sign].
         */
        abstract fun executeCommand(command: ByteArray): ByteArray?

        protected fun commitInputStates(states: List<StateRef>, txId: SecureHash, callerIdentity: Party) {
            log.debug { "Attempting to commit inputs for transaction: $txId" }
            val conflicts = mutableMapOf<StateRef, UniquenessProvider.ConsumingTx>()
            services.database.transaction {
                states.forEach { state ->
                    commitLog[state]?.let { conflicts[state] = it }
                }
                if (conflicts.isEmpty()) {
                    log.debug { "No conflicts detected, committing input states: ${states.joinToString()}" }
                    states.forEachIndexed { i, stateRef ->
                        val txInfo = UniquenessProvider.ConsumingTx(txId, i, callerIdentity)
                        commitLog.put(stateRef, txInfo)
                    }
                } else {
                    log.debug { "Conflict detected – the following inputs have already been committed: ${conflicts.keys.joinToString()}" }
                    val conflict = UniquenessProvider.Conflict(conflicts)
                    val conflictData = conflict.serialize()
                    val signedConflict = SignedData(conflictData, sign(conflictData.bytes))
                    throw NotaryException(NotaryError.Conflict(txId, signedConflict))
                }
            }
        }

        protected fun validateTimeWindow(t: TimeWindow?) {
            if (t != null && !timeWindowChecker.isValid(t))
                throw NotaryException(NotaryError.TimeWindowInvalid)
        }

        protected fun sign(bytes: ByteArray): DigitalSignature.WithKey {
            return services.database.transaction { services.keyManagementService.sign(bytes, services.notaryIdentityKey) }
        }

        // TODO:
        // - Test snapshot functionality with different bft-smart cluster configurations.
        // - Add streaming to support large data sets.
        override fun getSnapshot(): ByteArray {
            // LinkedHashMap for deterministic serialisation
            val m = LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>()
            services.database.transaction {
                commitLog.forEach { m[it.key] = it.value }
            }
            return m.serialize().bytes
        }

        override fun installSnapshot(bytes: ByteArray) {
            val m = bytes.deserialize<LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>>()
            services.database.transaction {
                commitLog.clear()
                commitLog.putAll(m)
            }
        }
    }
}

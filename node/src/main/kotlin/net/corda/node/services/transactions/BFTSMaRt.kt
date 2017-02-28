package net.corda.node.services.transactions

import bftsmart.tom.MessageContext
import bftsmart.tom.ServiceProxy
import bftsmart.tom.ServiceReplica
import bftsmart.tom.core.messages.TOMMessage
import bftsmart.tom.server.defaultservices.DefaultRecoverable
import bftsmart.tom.server.defaultservices.DefaultReplier
import bftsmart.tom.util.Extractor
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.Timestamp
import net.corda.core.crypto.*
import net.corda.core.node.services.TimestampChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.flows.NotaryError
import net.corda.flows.NotaryException
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.BFTSMaRt.Client
import net.corda.node.services.transactions.BFTSMaRt.Server
import net.corda.node.utilities.JDBCHashMap
import net.corda.node.utilities.databaseTransaction
import org.jetbrains.exposed.sql.Database
import java.util.*

/**
 * Implements a replicated transaction commit log based on the [BFT-SMaRt](https://github.com/bft-smart/library)
 * consensus algorithm. Every replica in the cluster is running a [Server] maintaining the state, and a[Client] is used
 * to to relay state modification requests to all [Server]s.
 */
// TODO: Write bft-smart host config file based on Corda node configuration.
// TODO: Define and document the configuration of the bft-smart cluster.
// TODO: Potentially update the bft-smart API for our use case or rebuild client and server from lower level building
//       blocks bft-smart provides.
// TODO: Support cluster membership changes. This requires reading about reconfiguration of bft-smart clusters and
//       perhaps a design doc. In general, it seems possible to use the state machine to reconfigure the cluster (reaching
//       consensus about  membership changes). Nodes that join the cluster for the first time or re-join can go through
//       a "recovering" state and request missing data from their peers.
object BFTSMaRt {
    /** Sent from [Client] to [Server]. */
    @CordaSerializable
    data class CommitRequest(val tx: Any, val callerIdentity: Party)

    /** Sent from [Server] to [Client]. */
    @CordaSerializable
    sealed class ReplicaResponse {
        class Error(val error: NotaryError) : ReplicaResponse()
        class Signature(val txSignature: DigitalSignature) : ReplicaResponse()
    }

    /** An aggregate response from all replica ([Server]) replies sent from [Client] back to the calling application. */
    @CordaSerializable
    sealed class ClusterResponse {
        class Error(val error: NotaryError) : ClusterResponse()
        class Signatures(val txSignatures: List<DigitalSignature>) : ClusterResponse()
    }

    class Client(val id: Int) : SingletonSerializeAsToken() {
        companion object {
            private val log = loggerFor<Client>()
        }

        /** A proxy for communicating with the BFT cluster */
        private val proxy: ServiceProxy by lazy { buildProxy() }

        /**
         * Sends a transaction commit request to the BFT cluster. The [proxy] will deliver the request to every
         * replica, and block until a sufficient number of replies are received.
         */
        fun commitTransaction(transaction: Any, otherSide: Party): ClusterResponse {
            require(transaction is FilteredTransaction || transaction is SignedTransaction) { "Unsupported transaction type: ${transaction.javaClass.name}" }
            val request = CommitRequest(transaction, otherSide)
            val responseBytes = proxy.invokeOrdered(request.serialize().bytes)
            val response = responseBytes.deserialize<ClusterResponse>()
            return response
        }

        private fun buildProxy(): ServiceProxy {
            val comparator = buildResponseComparator()
            val extractor = buildExtractor()
            return ServiceProxy(id, "bft-smart-config", comparator, extractor)
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
            return Extractor { replies, sameContent, lastReceived ->
                val responses = replies.mapNotNull { it?.content?.deserialize<ReplicaResponse>() }
                val accepted = responses.filterIsInstance<ReplicaResponse.Signature>()
                val rejected = responses.filterIsInstance<ReplicaResponse.Error>()

                log.debug { "BFT Client $id: number of replicas accepted the commit: ${accepted.size}, rejected: ${rejected.size}" }

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

    /**
     * Maintains the commit log and executes commit commands received from the [Client].
     *
     * The validation logic can be specified by implementing the [executeCommand] method.
     */
    @Suppress("LeakingThis")
    abstract class Server(val id: Int,
                          val db: Database,
                          tableName: String,
                          val services: ServiceHubInternal,
                          val timestampChecker: TimestampChecker) : DefaultRecoverable() {

        companion object {
            private val log = loggerFor<Server>()
        }

        // TODO: Use Requery with proper DB schema instead of JDBCHashMap.
        val commitLog = databaseTransaction(db) { JDBCHashMap<StateRef, UniquenessProvider.ConsumingTx>(tableName) }
        // TODO: Looks like this statement is blocking. Investigate the bft-smart node startup.
        val replica = ServiceReplica(id, "bft-smart-config", this, this, null, DefaultReplier())

        override fun appExecuteUnordered(command: ByteArray, msgCtx: MessageContext): ByteArray? {
            throw NotImplementedError("No unordered operations supported")
        }

        override fun appExecuteBatch(command: Array<ByteArray>, mcs: Array<MessageContext>): Array<ByteArray?> {
            val replies = command.zip(mcs) { c, m ->
                executeCommand(c)
            }
            return replies.toTypedArray()
        }

        /**
         * Implement logic to execute the command and commit the transaction to the log.
         * Helper methods are provided for transaction processing: [commitInputStates], [validateTimestamp], and [sign].
         */
        abstract fun executeCommand(command: ByteArray): ByteArray?

        protected fun commitInputStates(states: List<StateRef>, txId: SecureHash, callerIdentity: Party) {
            log.debug { "Attempting to commit inputs for transaction: $txId" }
            val conflicts = mutableMapOf<StateRef, UniquenessProvider.ConsumingTx>()
            databaseTransaction(db) {
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

        protected fun validateTimestamp(t: Timestamp?) {
            if (t != null && !timestampChecker.isValid(t))
                throw NotaryException(NotaryError.TimestampInvalid())
        }

        protected fun sign(bytes: ByteArray): DigitalSignature.WithKey {
            val mySigningKey = databaseTransaction(db) { services.notaryIdentityKey }
            return mySigningKey.signWithECDSA(bytes)
        }

        // TODO:
        // - Test snapshot functionality with different bft-smart cluster configurations.
        // - Add streaming to support large data sets.
        override fun getSnapshot(): ByteArray {
            // LinkedHashMap for deterministic serialisation
            val m = LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>()
            databaseTransaction(db) {
                commitLog.forEach { m[it.key] = it.value }
            }
            return m.serialize().bytes
        }

        override fun installSnapshot(bytes: ByteArray) {
            val m = bytes.deserialize<LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>>()
            databaseTransaction(db) {
                commitLog.clear()
                commitLog.putAll(m)
            }
        }
    }
}

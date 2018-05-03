/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import bftsmart.communication.ServerCommunicationSystem
import bftsmart.communication.client.netty.NettyClientServerCommunicationSystemClientSide
import bftsmart.communication.client.netty.NettyClientServerSession
import bftsmart.statemanagement.strategy.StandardStateManager
import bftsmart.tom.MessageContext
import bftsmart.tom.ServiceProxy
import bftsmart.tom.ServiceReplica
import bftsmart.tom.core.TOMLayer
import bftsmart.tom.core.messages.TOMMessage
import bftsmart.tom.server.defaultservices.DefaultRecoverable
import bftsmart.tom.server.defaultservices.DefaultReplier
import bftsmart.tom.util.Extractor
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.*
import net.corda.core.flows.NotarisationPayload
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.declaredField
import net.corda.core.internal.notary.NotaryInternalException
import net.corda.core.internal.notary.isConsumedByTheSameTx
import net.corda.core.internal.notary.validateTimeWindow
import net.corda.core.internal.toTypedArray
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.transactions.BFTSMaRt.Client
import net.corda.node.services.transactions.BFTSMaRt.Replica
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.nio.file.Path
import java.security.PublicKey
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
    data class CommitRequest(val payload: NotarisationPayload, val callerIdentity: Party)

    /** Sent from [Replica] to [Client]. */
    @CordaSerializable
    sealed class ReplicaResponse {
        data class Error(val error: SignedData<NotaryError>) : ReplicaResponse()
        data class Signature(val txSignature: TransactionSignature) : ReplicaResponse()
    }

    /** An aggregate response from all replica ([Replica]) replies sent from [Client] back to the calling application. */
    @CordaSerializable
    sealed class ClusterResponse {
        data class Error(val errors: List<SignedData<NotaryError>>) : ClusterResponse()
        data class Signatures(val txSignatures: List<TransactionSignature>) : ClusterResponse()
    }

    interface Cluster {
        /** Avoid bug where a replica fails to start due to a consensus change during the BFT startup sequence. */
        fun waitUntilAllReplicasHaveInitialized()
    }

    class Client(config: BFTSMaRtConfig, private val clientId: Int, private val cluster: Cluster, private val notaryService: BFTNonValidatingNotaryService) : SingletonSerializeAsToken() {
        companion object {
            private val log = contextLogger()
        }

        /** A proxy for communicating with the BFT cluster */
        private val proxy = ServiceProxy(clientId, config.path.toString(), buildResponseComparator(), buildExtractor())
        private val sessionTable = (proxy.communicationSystem as NettyClientServerCommunicationSystemClientSide).declaredField<Map<Int, NettyClientServerSession>>("sessionTable").value

        fun dispose() {
            proxy.close() // XXX: Does this do enough?
        }

        private fun awaitClientConnectionToCluster() {
            // TODO: Hopefully we only need to wait for the client's initial connection to the cluster, and this method can be moved to some startup code.
            // TODO: Investigate ConcurrentModificationException in this method.
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
        fun commitTransaction(payload: NotarisationPayload, otherSide: Party): ClusterResponse {
            awaitClientConnectionToCluster()
            cluster.waitUntilAllReplicasHaveInitialized()
            val requestBytes = CommitRequest(payload, otherSide).serialize().bytes
            val responseBytes = proxy.invokeOrdered(requestBytes)
            return responseBytes.deserialize()
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
                    ClusterResponse.Error(rejected.map { it.error })
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
                           createMap: () -> AppendOnlyPersistentMap<StateRef, SecureHash,
                                   BFTNonValidatingNotaryService.CommittedState, PersistentStateRef>,
                           protected val services: ServiceHubInternal,
                           protected val notaryIdentityKey: PublicKey) : DefaultRecoverable() {
        companion object {
            private val log = contextLogger()
        }

        private val stateManagerOverride = run {
            // Mock framework shutdown is not in reverse order, and we need to stop the faulty replicas first:
            val exposeStartupRace = config.exposeRaces && replicaId < maxFaultyReplicas(config.clusterSize)
            object : StandardStateManager() {
                override fun askCurrentConsensusId() {
                    if (exposeStartupRace) Thread.sleep(20000) // Must be long enough for the non-redundant replicas to reach a non-initial consensus.
                    super.askCurrentConsensusId()
                }
            }
        }

        override fun getStateManager() = stateManagerOverride
        // Must be initialised before ServiceReplica is started
        private val commitLog = services.database.transaction { createMap() }
        private val replica = run {
            config.waitUntilReplicaWillNotPrintStackTrace(replicaId)
            @Suppress("LeakingThis")
            CordaServiceReplica(replicaId, config.path, this)
        }

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
         * Helper methods are provided for transaction processing: [commitInputStates], and [sign].
         */
        abstract fun executeCommand(command: ByteArray): ByteArray?

        protected fun commitInputStates(states: List<StateRef>, txId: SecureHash, callerName: CordaX500Name, requestSignature: NotarisationRequestSignature, timeWindow: TimeWindow?) {
            log.debug { "Attempting to commit inputs for transaction: $txId" }
            services.database.transaction {
                logRequest(txId, callerName, requestSignature)
                val conflictingStates = LinkedHashMap<StateRef, StateConsumptionDetails>()
                for (state in states) {
                    commitLog[state]?.let { conflictingStates[state] = StateConsumptionDetails(it.sha256()) }
                }
                if (conflictingStates.isNotEmpty()) {
                    if (!isConsumedByTheSameTx(txId.sha256(), conflictingStates)) {
                        log.debug { "Failure, input states already committed: ${conflictingStates.keys}" }
                        throw NotaryInternalException(NotaryError.Conflict(txId, conflictingStates))
                    }
                } else {
                    val outsideTimeWindowError = validateTimeWindow(services.clock.instant(), timeWindow)
                    if (outsideTimeWindowError == null) {
                        states.forEach { commitLog[it] = txId }
                        log.debug { "Successfully committed all input states: $states" }
                    } else {
                        throw NotaryInternalException(outsideTimeWindowError)
                    }
                }
            }
        }

        private fun logRequest(txId: SecureHash, callerName: CordaX500Name, requestSignature: NotarisationRequestSignature) {
            val request = PersistentUniquenessProvider.Request(
                    consumingTxHash = txId.toString(),
                    partyName = callerName.toString(),
                    requestSignature = requestSignature.serialize().bytes,
                    requestDate = services.clock.instant()
            )
            val session = currentDBSession()
            session.persist(request)
        }

        /** Generates a signature over an arbitrary array of bytes. */
        protected fun sign(bytes: ByteArray): DigitalSignature.WithKey {
            return services.database.transaction { services.keyManagementService.sign(bytes, notaryIdentityKey) }
        }

        /** Generates a transaction signature over the specified transaction [txId]. */
        protected fun sign(txId: SecureHash): TransactionSignature {
            val signableData = SignableData(txId, SignatureMetadata(services.myInfo.platformVersion, Crypto.findSignatureScheme(notaryIdentityKey).schemeNumberID))
            return services.database.transaction { services.keyManagementService.sign(signableData, notaryIdentityKey) }
        }

        // TODO:
        // - Test snapshot functionality with different bft-smart cluster configurations.
        // - Add streaming to support large data sets.
        override fun getSnapshot(): ByteArray {
            // LinkedHashMap for deterministic serialisation
            val committedStates = LinkedHashMap<StateRef, SecureHash>()
            val requests = services.database.transaction {
                commitLog.allPersisted().forEach { committedStates[it.first] = it.second }
                val criteriaQuery = session.criteriaBuilder.createQuery(PersistentUniquenessProvider.Request::class.java)
                criteriaQuery.select(criteriaQuery.from(PersistentUniquenessProvider.Request::class.java))
                session.createQuery(criteriaQuery).resultList
            }
            return (committedStates to requests).serialize().bytes
        }

        override fun installSnapshot(bytes: ByteArray) {
            val (committedStates, requests) = bytes.deserialize<Pair<LinkedHashMap<StateRef, SecureHash>, List<PersistentUniquenessProvider.Request>>>()
            services.database.transaction {
                commitLog.clear()
                commitLog.putAll(committedStates)
                val deleteQuery = session.criteriaBuilder.createCriteriaDelete(PersistentUniquenessProvider.Request::class.java)
                deleteQuery.from(PersistentUniquenessProvider.Request::class.java)
                session.createQuery(deleteQuery).executeUpdate()
                requests.forEach { session.persist(it) }
            }
        }
    }
}

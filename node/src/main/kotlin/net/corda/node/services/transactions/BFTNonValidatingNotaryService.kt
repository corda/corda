package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.NotaryService
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.*
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.BFTSMaRtConfiguration
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import java.security.PublicKey
import javax.persistence.Entity
import javax.persistence.Table
import kotlin.concurrent.thread

/**
 * A non-validating notary service operated by a group of parties that don't necessarily trust each other.
 *
 * A transaction is notarised when the consensus is reached by the cluster on its uniqueness, and time-window validity.
 */
class BFTNonValidatingNotaryService(
        override val services: ServiceHubInternal,
        override val notaryIdentityKey: PublicKey,
        private val bftSMaRtConfig: BFTSMaRtConfiguration,
        cluster: BFTSMaRt.Cluster
) : NotaryService() {
    companion object {
        private val log = contextLogger()
    }

    private val client: BFTSMaRt.Client
    private val replicaHolder = SettableFuture.create<Replica>()

    init {
        client = BFTSMaRtConfig(bftSMaRtConfig.clusterAddresses, bftSMaRtConfig.debug, bftSMaRtConfig.exposeRaces).use {
            val replicaId = bftSMaRtConfig.replicaId
            val configHandle = it.handle()
            // Replica startup must be in parallel with other replicas, otherwise the constructor may not return:
            thread(name = "BFT SMaRt replica $replicaId init", isDaemon = true) {
                configHandle.use {
                    val timeWindowChecker = TimeWindowChecker(services.clock)
                    val replica = Replica(it, replicaId, { createMap() }, services, notaryIdentityKey, timeWindowChecker)
                    replicaHolder.set(replica)
                    log.info("BFT SMaRt replica $replicaId is running.")
                }
            }
            BFTSMaRt.Client(it, replicaId, cluster, this)
        }
    }

    fun waitUntilReplicaHasInitialized() {
        log.debug { "Waiting for replica ${bftSMaRtConfig.replicaId} to initialize." }
        replicaHolder.getOrThrow() // It's enough to wait for the ServiceReplica constructor to return.
    }

    fun commitTransaction(tx: Any, otherSide: Party) = client.commitTransaction(tx, otherSide)

    override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = ServiceFlow(otherPartySession, this)

    private class ServiceFlow(val otherSideSession: FlowSession, val service: BFTNonValidatingNotaryService) : FlowLogic<Void?>() {
        @Suspendable
        override fun call(): Void? {
            val stx = otherSideSession.receive<FilteredTransaction>().unwrap { it }
            val signatures = commit(stx)
            otherSideSession.send(signatures)
            return null
        }

        private fun commit(stx: FilteredTransaction): List<DigitalSignature> {
            val response = service.commitTransaction(stx, otherSideSession.counterparty)
            when (response) {
                is BFTSMaRt.ClusterResponse.Error -> throw NotaryException(response.error)
                is BFTSMaRt.ClusterResponse.Signatures -> {
                    log.debug("All input states of transaction ${stx.id} have been committed")
                    return response.txSignatures
                }
            }
        }
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}bft_committed_states")
    class PersistedCommittedState(id: PersistentStateRef, consumingTxHash: String, consumingIndex: Int, party: PersistentUniquenessProvider.PersistentParty)
        : PersistentUniquenessProvider.PersistentUniqueness(id, consumingTxHash, consumingIndex, party)

    private fun createMap(): AppendOnlyPersistentMap<StateRef, UniquenessProvider.ConsumingTx, PersistedCommittedState, PersistentStateRef> {
        return AppendOnlyPersistentMap(
                toPersistentEntityKey = { PersistentStateRef(it.txhash.toString(), it.index) },
                fromPersistentEntity = {
                    //TODO null check will become obsolete after making DB/JPA columns not nullable
                    val txId = it.id.txId ?: throw IllegalStateException("DB returned null SecureHash transactionId")
                    val index = it.id.index ?: throw IllegalStateException("DB returned null SecureHash index")
                    Pair(StateRef(txhash = SecureHash.parse(txId), index = index),
                            UniquenessProvider.ConsumingTx(
                                    id = SecureHash.parse(it.consumingTxHash),
                                    inputIndex = it.consumingIndex,
                                    requestingParty = Party(
                                            name = CordaX500Name.parse(it.party.name),
                                            owningKey = Crypto.decodePublicKey(it.party.owningKey))))
                },
                toPersistentEntity = { (txHash, index): StateRef, (id, inputIndex, requestingParty): UniquenessProvider.ConsumingTx ->
                    PersistedCommittedState(
                            id = PersistentStateRef(txHash.toString(), index),
                            consumingTxHash = id.toString(),
                            consumingIndex = inputIndex,
                            party = PersistentUniquenessProvider.PersistentParty(requestingParty.name.toString(),
                                    requestingParty.owningKey.encoded)
                    )
                },
                persistentEntityClass = PersistedCommittedState::class.java
        )
    }

    private class Replica(config: BFTSMaRtConfig,
                          replicaId: Int,
                          createMap: () -> AppendOnlyPersistentMap<StateRef, UniquenessProvider.ConsumingTx, PersistedCommittedState, PersistentStateRef>,
                          services: ServiceHubInternal,
                          notaryIdentityKey: PublicKey,
                          timeWindowChecker: TimeWindowChecker) : BFTSMaRt.Replica(config, replicaId, createMap, services, notaryIdentityKey, timeWindowChecker) {

        override fun executeCommand(command: ByteArray): ByteArray {
            val request = command.deserialize<BFTSMaRt.CommitRequest>()
            val ftx = request.tx as FilteredTransaction
            val response = verifyAndCommitTx(ftx, request.callerIdentity)
            return response.serialize().bytes
        }

        fun verifyAndCommitTx(ftx: FilteredTransaction, callerIdentity: Party): BFTSMaRt.ReplicaResponse {
            return try {
                val id = ftx.id
                val inputs = ftx.inputs
                val notary = ftx.notary
                validateTimeWindow(ftx.timeWindow)
                if (notary !in services.myInfo.legalIdentities) throw NotaryException(NotaryError.WrongNotary)
                commitInputStates(inputs, id, callerIdentity)
                log.debug { "Inputs committed successfully, signing $id" }
                BFTSMaRt.ReplicaResponse.Signature(sign(ftx))
            } catch (e: NotaryException) {
                log.debug { "Error processing transaction: ${e.error}" }
                BFTSMaRt.ReplicaResponse.Error(e.error)
            }
        }

    }

    override fun start() {
    }

    override fun stop() {
        replicaHolder.getOrThrow().dispose()
        client.dispose()
    }
}

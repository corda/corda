package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.SettableFuture
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.flows.NotaryException
import net.corda.core.flows.type.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.NotaryService
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.debug
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.node.utilities.NODE_DATABASE_PREFIX
import org.bouncycastle.asn1.x500.X500Name
import javax.persistence.Entity
import kotlin.concurrent.thread

/**
 * A non-validating notary service operated by a group of parties that don't necessarily trust each other.
 *
 * A transaction is notarised when the consensus is reached by the cluster on its uniqueness, and time-window validity.
 */
class BFTNonValidatingNotaryService(override val services: ServiceHubInternal, cluster: BFTSMaRt.Cluster = distributedCluster) : NotaryService() {
    companion object {
        val type = SimpleNotaryService.type.getSubType("bft")
        private val log = loggerFor<BFTNonValidatingNotaryService>()
        private val distributedCluster = object : BFTSMaRt.Cluster {
            override fun waitUntilAllReplicasHaveInitialized() {
                log.warn("A replica may still be initializing, in which case the upcoming consensus change may cause it to spin.")
            }
        }
    }

    private val client: BFTSMaRt.Client
    private val replicaHolder = SettableFuture.create<Replica>()

    init {
        require(services.configuration.bftSMaRt.isValid()) { "bftSMaRt replicaId must be specified in the configuration" }
        client = BFTSMaRtConfig(services.configuration.notaryClusterAddresses, services.configuration.bftSMaRt.debug, services.configuration.bftSMaRt.exposeRaces).use {
            val replicaId = services.configuration.bftSMaRt.replicaId
            val configHandle = it.handle()
            // Replica startup must be in parallel with other replicas, otherwise the constructor may not return:
            thread(name = "BFT SMaRt replica $replicaId init", isDaemon = true) {
                configHandle.use {
                    val timeWindowChecker = TimeWindowChecker(services.clock)
                    val replica = Replica(it, replicaId, { createMap() }, services, timeWindowChecker)
                    replicaHolder.set(replica)
                    log.info("BFT SMaRt replica $replicaId is running.")
                }
            }
            BFTSMaRt.Client(it, replicaId, cluster)
        }
    }

    fun waitUntilReplicaHasInitialized() {
        log.debug { "Waiting for replica ${services.configuration.bftSMaRt.replicaId} to initialize." }
        replicaHolder.getOrThrow() // It's enough to wait for the ServiceReplica constructor to return.
    }

    fun commitTransaction(tx: Any, otherSide: Party) = client.commitTransaction(tx, otherSide)

    override fun createServiceFlow(otherParty: Party): FlowLogic<Void?> = ServiceFlow(otherParty, this)

    private class ServiceFlow(val otherSide: Party, val service: BFTNonValidatingNotaryService) : FlowLogic<Void?>() {
        @Suspendable
        override fun call(): Void? {
            val stx = receive<FilteredTransaction>(otherSide).unwrap { it }
            val signatures = commit(stx)
            send(otherSide, signatures)
            return null
        }

        private fun commit(stx: FilteredTransaction): List<DigitalSignature> {
            val response = service.commitTransaction(stx, otherSide)
            when (response) {
                is BFTSMaRt.ClusterResponse.Error -> throw NotaryException(response.error)
                is BFTSMaRt.ClusterResponse.Signatures -> {
                    log.debug("All input states of transaction ${stx.rootHash} have been committed")
                    return response.txSignatures
                }
            }
        }
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}bft_smart_notary_committed_states")
    class PersistedCommittedState(id: PersistentStateRef, consumingTxHash: String, consumingIndex: Int, party: PersistentUniquenessProvider.PersistentParty)
        : PersistentUniquenessProvider.PersistentUniqueness(id, consumingTxHash, consumingIndex, party)

    fun createMap(): AppendOnlyPersistentMap<StateRef, UniquenessProvider.ConsumingTx, PersistedCommittedState, PersistentStateRef> =
            AppendOnlyPersistentMap(
                    toPersistentEntityKey = { PersistentStateRef(it.txhash.toString(), it.index) },
                    fromPersistentEntity = {
                        //TODO null check will become obsolete after making DB/JPA columns not nullable
                        var txId = it.id.txId ?: throw IllegalStateException("DB returned null SecureHash transactionId")
                        var index = it.id.index ?: throw IllegalStateException("DB returned null SecureHash index")
                        Pair(StateRef(txhash = SecureHash.parse(txId), index = index),
                            UniquenessProvider.ConsumingTx(
                                    id = SecureHash.parse(it.consumingTxHash),
                                    inputIndex = it.consumingIndex,
                                    requestingParty = Party(
                                            name = X500Name(it.party.name),
                                            owningKey = parsePublicKeyBase58(it.party.owningKey))))
                    },
                    toPersistentEntity = { (txHash, index) : StateRef, (id, inputIndex, requestingParty): UniquenessProvider.ConsumingTx ->
                        PersistedCommittedState(
                                id = PersistentStateRef(txHash.toString(), index),
                                consumingTxHash = id.toString(),
                                consumingIndex = inputIndex,
                                party = PersistentUniquenessProvider.PersistentParty(requestingParty.name.toString(),
                                        requestingParty.owningKey.toBase58String())
                        )
                    },
                    persistentEntityClass = PersistedCommittedState::class.java
            )

    private class Replica(config: BFTSMaRtConfig,
                          replicaId: Int,
                          createMap: () -> AppendOnlyPersistentMap<StateRef, UniquenessProvider.ConsumingTx, PersistedCommittedState, PersistentStateRef>,
                          services: ServiceHubInternal,
                          timeWindowChecker: TimeWindowChecker) : BFTSMaRt.Replica(config, replicaId, createMap, services, timeWindowChecker) {

        override fun executeCommand(command: ByteArray): ByteArray {
            val request = command.deserialize<BFTSMaRt.CommitRequest>()
            val ftx = request.tx as FilteredTransaction
            val response = verifyAndCommitTx(ftx, request.callerIdentity)
            return response.serialize().bytes
        }

        fun verifyAndCommitTx(ftx: FilteredTransaction, callerIdentity: Party): BFTSMaRt.ReplicaResponse {
            return try {
                val id = ftx.rootHash
                val inputs = ftx.filteredLeaves.inputs

                validateTimeWindow(ftx.filteredLeaves.timeWindow)
                commitInputStates(inputs, id, callerIdentity)

                log.debug { "Inputs committed successfully, signing $id" }
                val signableData = SignableData(id, SignatureMetadata(services.myInfo.platformVersion, Crypto.findSignatureScheme(services.notaryIdentityKey).schemeNumberID))
                val sig = sign(signableData)
                BFTSMaRt.ReplicaResponse.Signature(sig)
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

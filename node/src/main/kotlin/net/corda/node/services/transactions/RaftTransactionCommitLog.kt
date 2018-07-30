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

import io.atomix.catalyst.buffer.BufferInput
import io.atomix.catalyst.buffer.BufferOutput
import io.atomix.catalyst.serializer.Serializer
import io.atomix.catalyst.serializer.TypeSerializer
import io.atomix.copycat.Command
import io.atomix.copycat.Query
import io.atomix.copycat.server.Commit
import io.atomix.copycat.server.Snapshottable
import io.atomix.copycat.server.StateMachine
import io.atomix.copycat.server.storage.snapshot.SnapshotReader
import io.atomix.copycat.server.storage.snapshot.SnapshotWriter
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.notary.isConsumedByTheSameTx
import net.corda.core.internal.notary.validateTimeWindow
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.currentDBSession
import net.corda.serialization.internal.CordaSerializationEncoding
import java.time.Clock

/**
 * Notarised contract state commit log, replicated across a Copycat Raft cluster.
 *
 * Copycat ony supports in-memory state machines, so we back the state with JDBC tables.
 * State re-synchronisation is achieved by replaying the command log to the new (or re-joining) cluster member.
 */
class RaftTransactionCommitLog<E, EK>(
        private val db: CordaPersistence,
        private val nodeClock: Clock,
        createMap: () -> AppendOnlyPersistentMap<StateRef, Pair<Long, SecureHash>, E, EK>
) : StateMachine(), Snapshottable {
    object Commands {
        class CommitTransaction @JvmOverloads constructor(
                val states: List<StateRef>,
                val txId: SecureHash,
                val requestingParty: String,
                val requestSignature: ByteArray,
                val timeWindow: TimeWindow? = null,
                val references: List<StateRef> = emptyList()
        ) : Command<NotaryError?> {
            override fun compaction(): Command.CompactionMode {
                // The FULL compaction mode retains the command in the log until it has been stored and applied on all
                // servers in the cluster. Once the commit has been applied to a state machine and closed it may be
                // removed from the log during minor or major compaction.
                //
                // Note that we are not closing the commits, thus our log grows without bounds. We let the log grow on
                // purpose to be able to increase the size of a running cluster, e.g. to add and decommission nodes.
                // TODO: Cluster membership changes need testing.
                // TODO: I'm wondering if we should support resizing notary clusters, or if we could require users to
                // setup a new cluster of the desired size and transfer the data.
                return Command.CompactionMode.FULL
            }
        }

        class Get(val key: StateRef) : Query<SecureHash?>
    }

    private val map = db.transaction { createMap() }

    /** Commits the input states for the transaction as specified in the given [Commands.CommitTransaction]. */
    fun commitTransaction(raftCommit: Commit<Commands.CommitTransaction>): NotaryError? {
        val conflictingStates = LinkedHashMap<StateRef, StateConsumptionDetails>()

        fun checkConflict(states: List<StateRef>, type: StateConsumptionDetails.ConsumedStateType) = states.forEach { stateRef ->
            map[stateRef]?.let { conflictingStates[stateRef] = StateConsumptionDetails(it.second.sha256(), type) }
        }

        raftCommit.use {
            val index = it.index()
            return db.transaction {
                val commitCommand = raftCommit.command()
                logRequest(commitCommand)
                val txId = commitCommand.txId
                log.debug("State machine commit: attempting to store entries with keys (${commitCommand.states.joinToString()})")
                checkConflict(commitCommand.states, StateConsumptionDetails.ConsumedStateType.INPUT_STATE)
                checkConflict(commitCommand.references, StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE)
                if (conflictingStates.isNotEmpty()) {
                    if (isConsumedByTheSameTx(commitCommand.txId.sha256(), conflictingStates)) {
                        null
                    } else {
                        log.debug { "Failure, input states already committed: ${conflictingStates.keys}" }
                        NotaryError.Conflict(txId, conflictingStates)
                    }
                } else {
                    val outsideTimeWindowError = validateTimeWindow(clock.instant(), commitCommand.timeWindow)
                    if (outsideTimeWindowError == null) {
                        val entries = commitCommand.states.map { it to Pair(index, txId) }.toMap()
                        map.putAll(entries)
                        log.debug { "Successfully committed all input states: ${commitCommand.states}" }
                        null
                    } else {
                        outsideTimeWindowError
                    }
                }
            }
        }
    }

    private fun logRequest(commitCommand: RaftTransactionCommitLog.Commands.CommitTransaction) {
        val request = PersistentUniquenessProvider.Request(
                consumingTxHash = commitCommand.txId.toString(),
                partyName = commitCommand.requestingParty,
                requestSignature = commitCommand.requestSignature,
                requestDate = nodeClock.instant()
        )
        val session = currentDBSession()
        session.persist(request)
    }

    /** Gets the consuming transaction id for a given state reference. */
    fun get(commit: Commit<Commands.Get>): SecureHash? {
        commit.use {
            val key = it.operation().key
            return db.transaction { map[key]?.second }
        }
    }

    /**
     * Writes out all committed state and notarisation request entries to disk. Note that this operation does not
     * load all entries into memory, as the [SnapshotWriter] is using a disk-backed buffer internally, and iterating
     * map entries results in only a fixed number of recently accessed entries to ever be kept in memory.
     */
    override fun snapshot(writer: SnapshotWriter) {
        db.transaction {
            writer.writeInt(map.size)
            map.allPersisted().forEach {
                val bytes = it.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                writer.writeUnsignedShort(bytes.size)
                writer.writeObject(bytes)
            }

            val criteriaQuery = session.criteriaBuilder.createQuery(PersistentUniquenessProvider.Request::class.java)
            criteriaQuery.select(criteriaQuery.from(PersistentUniquenessProvider.Request::class.java))
            val results = session.createQuery(criteriaQuery).resultList

            writer.writeInt(results.size)
            results.forEach {
                val bytes = it.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                writer.writeUnsignedShort(bytes.size)
                writer.writeObject(bytes)
            }
        }
    }

    /** Reads entries from disk and populates the committed state and notarisation request tables. */
    override fun install(reader: SnapshotReader) {
        val size = reader.readInt()
        db.transaction {
            map.clear()
            // TODO: read & put entries in batches
            for (i in 1..size) {
                val bytes = ByteArray(reader.readUnsignedShort())
                reader.read(bytes)
                val (key, value) = bytes.deserialize<Pair<StateRef, Pair<Long, SecureHash>>>()
                map[key] = value
            }
            // Clean notarisation request log
            val deleteQuery = session.criteriaBuilder.createCriteriaDelete(PersistentUniquenessProvider.Request::class.java)
            deleteQuery.from(PersistentUniquenessProvider.Request::class.java)
            session.createQuery(deleteQuery).executeUpdate()
            // Load and populate request log
            for (i in 1..reader.readInt()) {
                val bytes = ByteArray(reader.readUnsignedShort())
                reader.read(bytes)
                val request = bytes.deserialize<PersistentUniquenessProvider.Request>()
                session.persist(request)
            }
        }
    }

    companion object {
        private val log = contextLogger()

        @VisibleForTesting
        val serializer: Serializer by lazy {
            Serializer().apply {
                registerAbstract(SecureHash::class.java, CordaKryoSerializer::class.java)
                registerAbstract(TimeWindow::class.java, CordaKryoSerializer::class.java)
                registerAbstract(NotaryError::class.java, CordaKryoSerializer::class.java)
                register(RaftTransactionCommitLog.Commands.CommitTransaction::class.java, CordaKryoSerializer::class.java)
                register(RaftTransactionCommitLog.Commands.Get::class.java, CordaKryoSerializer::class.java)
                register(StateRef::class.java, CordaKryoSerializer::class.java)
                register(LinkedHashMap::class.java, CordaKryoSerializer::class.java)
            }
        }

        class CordaKryoSerializer<T : Any> : TypeSerializer<T> {
            private val context = SerializationDefaults.CHECKPOINT_CONTEXT.withEncoding(CordaSerializationEncoding.SNAPPY)
            private val factory = SerializationFactory.defaultFactory

            override fun write(obj: T, buffer: BufferOutput<*>, serializer: Serializer) {
                val serialized = obj.serialize(context = context)
                buffer.writeInt(serialized.size)
                buffer.write(serialized.bytes)
            }

            override fun read(type: Class<T>, buffer: BufferInput<*>, serializer: Serializer): T {
                val size = buffer.readInt()
                val serialized = ByteArray(size)
                buffer.read(serialized)
                return factory.deserialize(ByteSequence.of(serialized), type, context)
            }
        }
    }
}
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
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.node.services.transactions.RaftUniquenessProvider.Companion.encoded
import net.corda.node.services.transactions.RaftUniquenessProvider.Companion.parseStateRef
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.time.Clock

/**
 * Notarised contract state commit log, replicated across a Copycat Raft cluster.
 *
 * Copycat ony supports in-memory state machines, so we back the state with JDBC tables.
 * State re-synchronisation is achieved by replaying the command log to the new (or re-joining) cluster member.
 */
class RaftTransactionCommitLog<E, EK>(
        val db: CordaPersistence,
        val nodeClock: Clock,
        createMap: () -> AppendOnlyPersistentMap<StateRef, Pair<Long, SecureHash>, E, EK>
) : StateMachine(), Snapshottable {
    object Commands {
        class CommitTransaction(
                val states: List<StateRef>,
                val txId: SecureHash,
                val requestingParty: String,
                val requestSignature: ByteArray
        ) : Command<Map<StateRef, SecureHash>> {
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
    fun commitTransaction(raftCommit: Commit<Commands.CommitTransaction>): Map<StateRef, SecureHash> {
        raftCommit.use {
            val index = it.index()
            val conflicts = LinkedHashMap<StateRef, SecureHash>()
            db.transaction {
                val commitCommand = raftCommit.command()
                logRequest(commitCommand)
                val states = commitCommand.states
                val txId = commitCommand.txId
                log.debug("State machine commit: storing entries with keys (${states.joinToString()})")
                for (state in states) {
                    map[state]?.let { conflicts[state] = it.second }
                }
                if (conflicts.isEmpty()) {
                    val entries = states.map { it to Pair(index, txId) }.toMap()
                    map.putAll(entries)
                }
            }
            return conflicts
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

        // Add custom serializers so Catalyst doesn't attempt to fall back on Java serialization for these types, which is disabled process-wide:
        val serializer: Serializer by lazy {
            Serializer().apply {
                register(RaftTransactionCommitLog.Commands.CommitTransaction::class.java) {
                    object : TypeSerializer<Commands.CommitTransaction> {
                        override fun write(obj: RaftTransactionCommitLog.Commands.CommitTransaction,
                                           buffer: BufferOutput<out BufferOutput<*>>,
                                           serializer: Serializer) {
                            buffer.writeUnsignedShort(obj.states.size)
                            with(serializer) {
                                obj.states.forEach {
                                    writeObject(it, buffer)
                                }
                                writeObject(obj.txId, buffer)
                            }
                            buffer.writeString(obj.requestingParty)
                            buffer.writeInt(obj.requestSignature.size)
                            buffer.write(obj.requestSignature)
                        }

                        override fun read(type: Class<RaftTransactionCommitLog.Commands.CommitTransaction>,
                                          buffer: BufferInput<out BufferInput<*>>,
                                          serializer: Serializer): RaftTransactionCommitLog.Commands.CommitTransaction {
                            val stateCount = buffer.readUnsignedShort()
                            val states = (1..stateCount).map {
                                serializer.readObject<StateRef>(buffer)
                            }
                            val txId = serializer.readObject<SecureHash>(buffer)
                            val name = buffer.readString()
                            val signatureSize = buffer.readInt()
                            val signature = ByteArray(signatureSize)
                            buffer.read(signature)
                            return RaftTransactionCommitLog.Commands.CommitTransaction(states, txId, name, signature)
                        }
                    }
                }
                register(RaftTransactionCommitLog.Commands.Get::class.java) {
                    object : TypeSerializer<Commands.Get> {
                        override fun write(obj: RaftTransactionCommitLog.Commands.Get, buffer: BufferOutput<out BufferOutput<*>>, serializer: Serializer) {
                            serializer.writeObject(obj.key, buffer)
                        }

                        override fun read(type: Class<RaftTransactionCommitLog.Commands.Get>, buffer: BufferInput<out BufferInput<*>>, serializer: Serializer): RaftTransactionCommitLog.Commands.Get {
                            val key = serializer.readObject<StateRef>(buffer)
                            return RaftTransactionCommitLog.Commands.Get(key)
                        }

                    }
                }
                register(StateRef::class.java) {
                    object : TypeSerializer<StateRef> {
                        override fun write(obj: StateRef, buffer: BufferOutput<out BufferOutput<*>>, serializer: Serializer) {
                            buffer.writeString(obj.encoded())
                        }

                        override fun read(type: Class<StateRef>, buffer: BufferInput<out BufferInput<*>>, serializer: Serializer): StateRef {
                            return buffer.readString().parseStateRef()
                        }
                    }
                }
                registerAbstract(SecureHash::class.java) {
                    object : TypeSerializer<SecureHash> {
                        override fun write(obj: SecureHash, buffer: BufferOutput<out BufferOutput<*>>, serializer: Serializer) {
                            buffer.writeUnsignedShort(obj.bytes.size)
                            buffer.write(obj.bytes)
                        }

                        override fun read(type: Class<SecureHash>, buffer: BufferInput<out BufferInput<*>>, serializer: Serializer): SecureHash {
                            val size = buffer.readUnsignedShort()
                            val bytes = ByteArray(size)
                            buffer.read(bytes)
                            return SecureHash.SHA256(bytes)
                        }
                    }
                }
                register(LinkedHashMap::class.java) {
                    object : TypeSerializer<LinkedHashMap<*, *>> {
                        override fun write(obj: LinkedHashMap<*, *>, buffer: BufferOutput<out BufferOutput<*>>, serializer: Serializer) {
                            buffer.writeInt(obj.size)
                            obj.forEach {
                                with(serializer) {
                                    writeObject(it.key, buffer)
                                    writeObject(it.value, buffer)
                                }
                            }
                        }

                        override fun read(type: Class<LinkedHashMap<*, *>>, buffer: BufferInput<out BufferInput<*>>, serializer: Serializer): LinkedHashMap<*, *> {
                            return LinkedHashMap<Any, Any>().apply {
                                repeat(buffer.readInt()) {
                                    put(serializer.readObject(buffer), serializer.readObject(buffer))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

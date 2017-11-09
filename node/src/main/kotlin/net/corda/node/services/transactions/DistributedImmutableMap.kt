package net.corda.node.services.transactions

import io.atomix.copycat.Command
import io.atomix.copycat.Query
import io.atomix.copycat.server.Commit
import io.atomix.copycat.server.Snapshottable
import io.atomix.copycat.server.StateMachine
import io.atomix.copycat.server.storage.snapshot.SnapshotReader
import io.atomix.copycat.server.storage.snapshot.SnapshotWriter
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.*
import java.util.LinkedHashMap

/**
 * A distributed map state machine that doesn't allow overriding values. The state machine is replicated
 * across a Copycat Raft cluster.
 *
 * The map contents are backed by a JDBC table. State re-synchronisation is achieved by periodically persisting snapshots
 * to disk, and sharing them across the cluster. A new node joining the cluster will have to obtain and install a snapshot
 * containing the entire JDBC table contents.
 */
class DistributedImmutableMap<K : Any, V : Any, E, EK>(val db: CordaPersistence, createMap: () -> AppendOnlyPersistentMap<K, V, E, EK>) : StateMachine() {
    companion object {
        private val log = loggerFor<DistributedImmutableMap<*, *, *, *>>()
    }

    object Commands {
        class PutAll<K, V>(val entries: Map<K, V>) : Command<Map<K, V>> {
            override fun compaction(): Command.CompactionMode {
                // The SNAPSHOT compaction mode indicates that a command can be removed from the Raft log once
                // a snapshot of the state machine has been written to disk
                return Command.CompactionMode.SNAPSHOT
            }
        }

        class Size : Query<Int>
        class Get<out K, V>(val key: K) : Query<V?>
    }

    private val map = db.transaction { createMap() }

    /** Gets a value for the given [Commands.Get.key] */
    fun get(commit: Commit<Commands.Get<K, V>>): V? {
        commit.use {
            val key = it.operation().key
            return db.transaction { map[key] }
        }
    }

    /**
     * Stores the given [Commands.PutAll.entries] if no entry key already exists.
     *
     * @return map containing conflicting entries
     */
    fun put(commit: Commit<Commands.PutAll<K, V>>): Map<K, V> {
        commit.use {
            val conflicts = LinkedHashMap<K, V>()
            db.transaction {
                val entries = commit.operation().entries
                log.debug("State machine commit: storing entries with keys (${entries.keys.joinToString()})")
                for (key in entries.keys) map[key]?.let { conflicts[key] = it }
                if (conflicts.isEmpty()) map.putAll(entries)
            }
            return conflicts
        }
    }

    fun size(commit: Commit<Commands.Size>): Int {
        commit.use { _ ->
            return db.transaction { map.size }
        }
    }
}

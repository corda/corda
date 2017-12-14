package net.corda.node.services.transactions

import io.atomix.copycat.Command
import io.atomix.copycat.Query
import io.atomix.copycat.server.Commit
import io.atomix.copycat.server.StateMachine
import net.corda.core.utilities.contextLogger
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.util.*

/**
 * A distributed map state machine that doesn't allow overriding values. The state machine is replicated
 * across a Copycat Raft cluster.
 *
 * The map contents are backed by a JDBC table. State re-synchronisation is achieved by replaying the command log to the
 * new (or re-joining) cluster member.
 */
class DistributedImmutableMap<K : Any, V : Any, E, EK>(val db: CordaPersistence, createMap: () -> AppendOnlyPersistentMap<K, Pair<Long, V>, E, EK>) : StateMachine() {
    companion object {
        private val log = contextLogger()
    }

    object Commands {
        class PutAll<K, V>(val entries: Map<K, V>) : Command<Map<K, V>> {
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

        class Size : Query<Int>
        class Get<out K, V>(val key: K) : Query<V?>
    }

    private val map = db.transaction { createMap() }

    /** Gets a value for the given [Commands.Get.key] */
    fun get(commit: Commit<Commands.Get<K, V>>): V? {
        commit.use {
            val key = it.operation().key
            return db.transaction { map[key]?.second }
        }
    }

    /**
     * Stores the given [Commands.PutAll.entries] if no entry key already exists.
     *
     * @return map containing conflicting entries
     */
    fun put(commit: Commit<Commands.PutAll<K, V>>): Map<K, V> {
        commit.use {
            val index = commit.index()
            val conflicts = LinkedHashMap<K, V>()
            db.transaction {
                val entries = commit.operation().entries
                log.debug("State machine commit: storing entries with keys (${entries.keys.joinToString()})")
                for (key in entries.keys) map[key]?.let { conflicts[key] = it.second }
                if (conflicts.isEmpty()) map.putAll(entries.mapValues { Pair(index, it.value) })
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

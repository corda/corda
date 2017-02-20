package net.corda.node.services.transactions

import bftsmart.tom.ServiceProxy
import bftsmart.tom.MessageContext
import bftsmart.tom.ServiceReplica
import bftsmart.tom.server.defaultservices.DefaultRecoverable
import bftsmart.tom.server.defaultservices.DefaultReplier
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.utilities.JDBCHashMap
import net.corda.node.utilities.databaseTransaction
import org.jetbrains.exposed.sql.Database
import java.util.LinkedHashMap

enum class RequestType {
    Get,
    Put
}

/** Sent from [BFTSmartClient] to [BFTSmartServer] */
data class Request(val type: RequestType, val data: Any)

class BFTSmartClient<K: Any, V: Any>(id: Int) {

    val clientProxy = ServiceProxy(id, "bft-smart-config")

    /**
     * Returns conflicts as a map of conflicting keys and their stored values or an empty map if all entries are
     * committed without conflicts.
     */
    fun put(entries: Map<K, V>): Map<K, V> {
        val request = Request(RequestType.Put, entries)
        val responseBytes = clientProxy.invokeOrdered(request.serialize().bytes)
        return responseBytes.deserialize<Map<K, V>>()
    }

    /** Returns the value associated with the key or null if no value is stored under the key. */
    fun get(key: K): V? {
        val request = Request(RequestType.Get, key)
        val responseBytes = clientProxy.invokeUnordered(request.serialize().bytes) ?: return null
        return responseBytes.deserialize<V>()
    }
}

class BFTSmartServer<K: Any, V: Any>(val id: Int, val db: Database, tableName: String) : DefaultRecoverable() {
    // TODO: Exception handling when processing client input.

    // TODO: Use Requery with proper DB schema instead of JDBCHashMap.
    val table = databaseTransaction(db) { JDBCHashMap<K, V>(tableName) }

    // TODO: Looks like this statement is blocking. Investigate the bft-smart node startup.
    val replica = ServiceReplica(id, "bft-smart-config", this, this, null, DefaultReplier())

    @Suppress("UNUSED_PARAMETER")
    override fun appExecuteUnordered(command: ByteArray, msgCtx: MessageContext): ByteArray? {
        // TODO: collect signatures from msgCtx
        val request = command.deserialize<Request>()
        when (request.type) {
            RequestType.Get -> {
                val v = databaseTransaction(db) { table[request.data] } ?: return null
                return v.serialize().bytes
            }
            else -> {
                throw Exception("Unhandled request type: ${request.type}")
            }
        }
    }

    override fun appExecuteBatch(command: Array<ByteArray>, mcs: Array<MessageContext>): Array<ByteArray?> {
        val replies = command.zip(mcs) { c, m ->
            executeSingle(c, m)
        }
        return replies.toTypedArray()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun executeSingle(command: ByteArray, msgCtx: MessageContext): ByteArray? {
        // TODO: collect signatures from msgCtx
        val request = command.deserialize<Request>()
        val conflicts = mutableMapOf<K, V>()
        when (request.type) {
            RequestType.Put -> {
                @Suppress("UNCHECKED_CAST")
                val m = request.data as Map<K, V>
                databaseTransaction(db) {
                    for (k in m.keys) table[k]?.let { conflicts[k] = it }
                    if (conflicts.isEmpty()) table.putAll(m)
                }
            }
            else -> {
                throw Exception("Unhandled request type: ${request.type}")
            }
        }
        return conflicts.serialize().bytes
    }

    // TODO:
    // - Test snapshot functionality with different bft-smart cluster configurations.
    // - Add streaming to support large data sets.
    override fun getSnapshot(): ByteArray {
        // LinkedHashMap for deterministic serialisation
        // TODO: Simply use an array of pairs.
        val m = LinkedHashMap<K, V>()
        databaseTransaction(db) {
            table.forEach { m[it.key] = it.value }
        }
        return m.serialize().bytes
    }

    override fun installSnapshot(bytes: ByteArray) {
        val m = bytes.deserialize<LinkedHashMap<K, V>>()
        databaseTransaction(db) {
            table.clear()
            table.putAll(m)
        }
    }
}

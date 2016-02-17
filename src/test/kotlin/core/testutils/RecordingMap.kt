/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.testutils

import core.utilities.loggerFor
import org.slf4j.Logger
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * A RecordingMap wraps a regular Map<K, V> and records the sequence of gets and puts to it. This is useful in
 * white box unit tests to ensure that code is accessing a data store as much as you expect.
 *
 * Note: although this class itself thread safe, if the underlying map is not, then this class loses its thread safety.
 */
@ThreadSafe
class RecordingMap<K, V>(private val wrappedMap: MutableMap<K, V>,
                         private val logger: Logger = loggerFor<RecordingMap<K, V>>()) : MutableMap<K, V> by wrappedMap {
    // If/when Kotlin supports data classes inside sealed classes, that would be preferable to this.
    interface Record
    data class Get<K>(val key: K) : Record
    data class Put<K, V>(val key: K, val value: V) : Record

    private val _records = Collections.synchronizedList(ArrayList<Record>())

    /** Returns a snapshot of the set of records */
    val records: List<Record> get() = _records.toList()

    fun clearRecords() = _records.clear()

    override fun get(key: K): V? {
        _records.add(Get(key))
        logger.trace("GET ${logger.name} : $key = ${wrappedMap[key]}")
        return wrappedMap[key]
    }

    override fun put(key: K, value: V): V? {
        _records.add(Put(key, value))
        logger.trace("PUT ${logger.name} : $key = $value")
        return wrappedMap.put(key, value)
    }

    override fun putAll(from: Map<out K, V>) {
        for ((k, v) in from) {
            put(k, v)
        }
    }

    fun putAllUnrecorded(from: Map<out K, V>) {
        wrappedMap.putAll(from)
    }
}

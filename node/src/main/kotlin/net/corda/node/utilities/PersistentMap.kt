package net.corda.node.utilities


import com.google.common.cache.RemovalCause
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import net.corda.core.utilities.loggerFor
import java.util.*


/**
 * Implements an unbound caching layer on top of a table accessed via Hibernate mapping.
 */
class PersistentMap<K, V, E, EK> (
        val toPersistentEntityKey: (K) -> EK,
        val fromPersistentEntity: (E) -> Pair<K,V>,
        val toPersistentEntity: (key: K, value: V) -> E,
        val persistentEntityClass: Class<E>
) : MutableMap<K, V>, AbstractMap<K, V>() {

    private companion object {
        val log = loggerFor<PersistentMap<*, *, *, *>>()
    }

    private val cache = NonInvalidatingUnboundCache(
            concurrencyLevel = 8,
            loadFunction = { key -> Optional.ofNullable(loadValue(key)) },
            removalListener = ExplicitRemoval(toPersistentEntityKey, persistentEntityClass)
    )

    class ExplicitRemoval<K, V, E, EK>(val toPersistentEntityKey: (K) -> EK, val persistentEntityClass: Class<E>): RemovalListener<K,V> {
        override fun onRemoval(notification: RemovalNotification<K, V>?) {
            when (notification?.cause) {
                RemovalCause.EXPLICIT -> {
                    val session = DatabaseTransactionManager.current().session
                    val elem = session.find(persistentEntityClass, toPersistentEntityKey(notification.key))
                    if (elem != null) {
                        session.remove(elem)
                    }
                }
                RemovalCause.EXPIRED, RemovalCause.SIZE, RemovalCause.COLLECTED -> {
                    log.error("Entry was removed from cache!!!")
                }
                //else do nothing for RemovalCause.REPLACED
            }
        }
    }

    override operator fun get(key: K): V? {
        return cache.get(key).orElse(null)
    }

    fun all(): Sequence<Pair<K, V>> {
        return cache.asMap().map { entry -> Pair(entry.key as K, entry.value as V) }.asSequence()
    }

    override val size = all().count()

    private tailrec fun set(key: K, value: V, logWarning: Boolean = true, store: (K,V) -> Boolean) : Boolean {
        var inserted = false
        var uniqueInDb = true
        val existingInCache = cache.get(key) { //thread safe, if multiple threads may wait until the first one has loaded
            inserted = true
            // Value wasn't in the cache and wasn't in DB (because the cache is unbound).
            // Store the value, depending on store implementation this may overwrite existing entry in DB.
            uniqueInDb = store(key, value)
            Optional.of(value)
        }
        if (!inserted) {
            if (existingInCache.isPresent) {
                // Value was cached already, store the new value in the DB (depends on tore implementation) and refresh cache.
                uniqueInDb = false
                synchronized(this) {
                    merge(key, value)
                    cache.put(key,Optional.of(value))
                }
            } else {
                // This happens when the key was queried before with no value associated. We invalidate the cached null
                // value and recursively call set again. This is to avoid race conditions where another thread queries after
                // the invalidate but before the set.
                cache.invalidate(key)
                return set(key, value, logWarning, store)
            }
        }
        if (logWarning && !uniqueInDb && logWarning) {
            log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, not inserting the second time")
        }
        return uniqueInDb
    }

    /**
     * Associates the specified value with the specified key in this map and persists it.
     */
    operator fun set(key: K, value: V) {
        set(key, value, logWarning = false) {
            key,value ->  DatabaseTransactionManager.current().session.save(toPersistentEntity(key,value))
            true
        }
    }

    private fun merge(key: K, value: V): Boolean {
        val prev = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
        return if (prev != null) {
            DatabaseTransactionManager.current().session.merge(toPersistentEntity(key,value))
            false
        } else {
            DatabaseTransactionManager.current().session.save(toPersistentEntity(key,value))
            true
        }
    }
    /**
     * Associates the specified value with the specified key in this map and persists it.
     * @return true if added key was unique, otherwise false
     */
    fun addWithDuplicatesAllowed(key: K, value: V): Boolean =
        set(key, value) { k, v -> merge(k,v)
        }

    private fun loadValue(key: K): V? {
        val result = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.let(fromPersistentEntity)?.second
    }

    /**
     * Removes the mapping for the specified key from this map and underlying storage if present.
     */
    override fun remove(key: K): V? {
        val result = cache.get(key).orElse(null)
        cache.invalidate(key)
        return result
    }

    private class NotReallyMutableEntry<K, V>(key: K, value: V/*, val seqNo: Int*/) : AbstractMap.SimpleImmutableEntry<K, V>(key, value), MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            throw UnsupportedOperationException("Not really mutable.  Implement if really required.")
        }
    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<K, V>> {
        private val iterator = all().map { NotReallyMutableEntry(it.first, it.second) }.iterator()

        private var current: MutableMap.MutableEntry<K, V>? = null

        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): MutableMap.MutableEntry<K, V> {
            val extractedNext = iterator.next()
            current = extractedNext
            return extractedNext
        }

        override fun remove() {
            val savedCurrent = current ?: throw IllegalStateException("Not called next() yet or already removed.")
            current = null
            remove(savedCurrent.key)
        }
    }

    override val keys: MutableSet<K> get() {
        return object : AbstractSet<K>() {
            override val size: Int get() = this@PersistentMap.size
            override fun iterator(): MutableIterator<K> {
                return object : MutableIterator<K> {
                    private val entryIterator = EntryIterator()

                    override fun hasNext(): Boolean = entryIterator.hasNext()
                    override fun next(): K = entryIterator.next().key
                    override fun remove() {
                        entryIterator.remove()
                    }
                }
            }
        }
    }

    override val values: MutableCollection<V> get() {
        return object : AbstractCollection<V>() {
            override val size: Int get() = this@PersistentMap.size
            override fun iterator(): MutableIterator<V> {
                return object : MutableIterator<V> {
                    private val entryIterator = EntryIterator()

                    override fun hasNext(): Boolean = entryIterator.hasNext()
                    override fun next(): V = entryIterator.next().value
                    override fun remove() {
                        entryIterator.remove()
                    }
                }
            }
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() {
        return object : AbstractSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int get() = this@PersistentMap.size
            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                    private val entryIterator = EntryIterator()

                    override fun hasNext(): Boolean = entryIterator.hasNext()
                    override fun next(): MutableMap.MutableEntry<K, V> = entryIterator.next()
                    override fun remove() {
                        entryIterator.remove()
                    }
                }
            }
        }
    }

    override fun put(key: K, value: V): V? {
        val old = cache.get(key)
        addWithDuplicatesAllowed(key, value)
        return old.orElse(null)
    }
}

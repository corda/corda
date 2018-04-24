package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.util.*

/**
 * Implements an unbound caching layer on top of a table accessed via Hibernate mapping.
 */
class PersistentMap<K : Any, V, E, out EK>(
        val toPersistentEntityKey: (K) -> EK,
        val fromPersistentEntity: (E) -> Pair<K, V>,
        val toPersistentEntity: (key: K, value: V) -> E,
        val persistentEntityClass: Class<E>
) : MutableMap<K, V>, AbstractMap<K, V>() {

    private companion object {
        private val log = contextLogger()
    }

    private val cache = NonInvalidatingUnboundCache(
            loadFunction = { key -> Optional.ofNullable(loadValue(key)) },
            removalListener = ExplicitRemoval(toPersistentEntityKey, persistentEntityClass)
    ).apply {
        //preload to allow all() to take data only from the cache (cache is unbound)
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(persistentEntityClass)
        criteriaQuery.select(criteriaQuery.from(persistentEntityClass))
        getAll(session.createQuery(criteriaQuery).resultList.map { e -> fromPersistentEntity(e as E).first }.asIterable())
    }

    class ExplicitRemoval<K, V, E, EK>(private val toPersistentEntityKey: (K) -> EK, private val persistentEntityClass: Class<E>) : RemovalListener<K, V> {
        override fun onRemoval(key: K?, value: V?, cause: RemovalCause) {
            when (cause) {
                RemovalCause.EXPLICIT -> {
                    val session = currentDBSession()
                    val elem = session.find(persistentEntityClass, toPersistentEntityKey(key!!))
                    if (elem != null) {
                        session.remove(elem)
                    }
                }
                RemovalCause.EXPIRED, RemovalCause.SIZE, RemovalCause.COLLECTED -> {
                    log.error("Entry was removed from cache!!!")
                }
                RemovalCause.REPLACED -> {
                }
            }
        }
    }

    override operator fun get(key: K): V? {
        return cache.get(key)!!.orElse(null)
    }

    fun all(): Sequence<Pair<K, V>> {
        return cache.asMap().asSequence().filter { it.value.isPresent }.map { Pair(it.key, it.value.get()) }
    }

    override val size get() = cache.estimatedSize().toInt()

    private tailrec fun set(key: K, value: V): Boolean {
        var insertionAttempt = false
        var isUnique = true
        val existingInCache = cache.get(key) {
            // Thread safe, if multiple threads may wait until the first one has loaded.
            insertionAttempt = true
            // Value wasn't in the cache and wasn't in DB (because the cache is unbound).
            // Store the value, depending on store implementation this may replace existing entry in DB.
            merge(key, value)
            Optional.of(value)
        }!!
        if (!insertionAttempt) {
            if (existingInCache.isPresent) {
                // Key already exists in cache, store the new value in the DB (depends on tore implementation) and refresh cache.
                isUnique = false
                replaceValue(key, value)
            } else {
                // This happens when the key was queried before with no value associated. We invalidate the cached null
                // value and recursively call set again. This is to avoid race conditions where another thread queries after
                // the invalidate but before the set.
                cache.invalidate(key)
                return set(key, value)
            }
        }
        return isUnique
    }

    private fun replaceValue(key: K, value: V) {
        synchronized(this) {
            merge(key, value)
            cache.put(key, Optional.of(value))
        }
    }

    private fun merge(key: K, value: V): V? {
        val session = currentDBSession()
        val existingEntry = session.find(persistentEntityClass, toPersistentEntityKey(key))
        return if (existingEntry != null) {
            session.merge(toPersistentEntity(key, value))
            fromPersistentEntity(existingEntry).second
        } else {
            session.save(toPersistentEntity(key, value))
            null
        }
    }

    private fun loadValue(key: K): V? {
        val result = currentDBSession().find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.let(fromPersistentEntity)?.second
    }

    /**
     * Removes the mapping for the specified key from this map and underlying storage if present.
     */
    override fun remove(key: K): V? {
        val result = cache.get(key)!!.orElse(null)
        cache.invalidate(key)
        return result
    }

    private class NotReallyMutableEntry<K, V>(key: K, value: V) : AbstractMap.SimpleImmutableEntry<K, V>(key, value), MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            throw UnsupportedOperationException("Not really mutable. Implement if really required.")
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

    override val keys: MutableSet<K>
        get() {
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

    override val values: MutableCollection<V>
        get() {
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

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
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

    /**
     * Associates the specified value with the specified key in this map and persists it.
     * @return true if added key was unique, otherwise false
     */
    override fun put(key: K, value: V): V? {
        val old = cache.get(key)
        set(key, value)
        return old!!.orElse(null)
    }

    fun load() {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(persistentEntityClass)
        criteriaQuery.select(criteriaQuery.from(persistentEntityClass))
        cache.getAll(session.createQuery(criteriaQuery).resultList.map { e -> fromPersistentEntity(e as E).first }.asIterable())
    }
}

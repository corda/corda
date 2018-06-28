/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Weigher
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.contextTransaction
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


/**
 * Implements a caching layer on top of an *append-only* table accessed via Hibernate mapping. Note that if the same key is [set] twice,
 * typically this will result in a duplicate insert if this is racing with another transaction.  The flow framework will then retry.
 *
 * This class relies heavily on the fact that compute operations in the cache are atomic for a particular key.
 */
abstract class AppendOnlyPersistentMapBase<K, V, E, out EK>(
        val toPersistentEntityKey: (K) -> EK,
        val fromPersistentEntity: (E) -> Pair<K, V>,
        val toPersistentEntity: (key: K, value: V) -> E,
        val persistentEntityClass: Class<E>
) {

    private companion object {
        private val log = contextLogger()
    }

    protected abstract val cache: LoadingCache<K, Transactional<V>>
    protected val pendingKeys = ConcurrentHashMap<K, MutableSet<DatabaseTransaction>>()

    /**
     * Returns the value associated with the key, first loading that value from the storage if necessary.
     */
    operator fun get(key: K): V? {
        return cache.get(key)!!.orElse(null)
    }

    val size get() = allPersisted().toList().size

    /**
     * Returns all key/value pairs from the underlying storage.
     */
    fun allPersisted(): Sequence<Pair<K, V>> {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(persistentEntityClass)
        val root = criteriaQuery.from(persistentEntityClass)
        criteriaQuery.select(root)
        val query = session.createQuery(criteriaQuery)
        val result = query.resultList
        return result.map { x -> fromPersistentEntity(x) }.asSequence()
    }

    private fun set(key: K, value: V, logWarning: Boolean, store: (K, V) -> V?): Boolean {
        // Will be set to true if store says it isn't in the database.
        var isUnique = false
        cache.asMap().compute(key) { _, oldValue ->
            // Always write to the database, unless we can see it's already committed.
            when (oldValue) {
                is Transactional.InFlight<*, V> -> {
                    // Someone else is writing, so store away!
                    // TODO: we can do collision detection here and prevent it happening in the database.  But we also have to do deadlock detection, so a bit of work.
                    isUnique = (store(key, value) == null)
                    oldValue.apply { alsoWrite(value) }
                }
                is Transactional.Committed<V> -> oldValue // The value is already globally visible and cached.  So do nothing since the values are always the same.
                else -> {
                    // Null or Missing.  Store away!
                    isUnique = (store(key, value) == null)
                    if (!isUnique && !weAreWriting(key)) {
                        // If we found a value already in the database, and we were not already writing, then it's already committed but got evicted.
                        Transactional.Committed(value)
                    } else {
                        // Some database transactions, including us, writing, with readers seeing whatever is in the database and writers seeing the (in memory) value.
                        Transactional.InFlight(this, key, { loadValue(key) }).apply { alsoWrite(value) }
                    }
                }

            }
        }
        if (logWarning && !isUnique) {
            log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, not inserting the second time")
        }
        return isUnique
    }

    /**
     * Associates the specified value with the specified key in this map and persists it.
     * If the map previously contained a mapping for the key, the behaviour is unpredictable and may throw an error from the underlying storage.
     */
    operator fun set(key: K, value: V) =
            set(key, value, logWarning = false) { k, v ->
                currentDBSession().save(toPersistentEntity(k, v))
                null
            }

    /**
     * Associates the specified value with the specified key in this map and persists it.
     * If the map previously contained a committed mapping for the key, the old value is not replaced.  It may throw an error from the
     * underlying storage if this races with another database transaction to store a value for the same key.
     * @return true if added key was unique, otherwise false
     */
    fun addWithDuplicatesAllowed(key: K, value: V, logWarning: Boolean = true): Boolean =
            set(key, value, logWarning) { k, v ->
                val session = currentDBSession()
                val existingEntry = session.find(persistentEntityClass, toPersistentEntityKey(k))
                if (existingEntry == null) {
                    session.save(toPersistentEntity(k, v))
                    null
                } else {
                    fromPersistentEntity(existingEntry).second
                }
            }

    fun putAll(entries: Map<K, V>) {
        entries.forEach {
            set(it.key, it.value)
        }
    }

    protected fun loadValue(key: K): V? {
        val result = currentDBSession().find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.apply { currentDBSession().detach(result) }?.let(fromPersistentEntity)?.second
    }

    operator fun contains(key: K) = get(key) != null

    /**
     * Removes all of the mappings from this map and underlying storage. The map will be empty after this call returns.
     * WARNING!! The method is not thread safe.
     */
    fun clear() {
        val session = currentDBSession()
        val deleteQuery = session.criteriaBuilder.createCriteriaDelete(persistentEntityClass)
        deleteQuery.from(persistentEntityClass)
        session.createQuery(deleteQuery).executeUpdate()
        cache.invalidateAll()
    }

    // Helpers to know if transaction(s) are currently writing the given key.
    protected fun weAreWriting(key: K): Boolean = pendingKeys.get(key)?.contains(contextTransaction) ?: false
    protected fun anyoneWriting(key: K): Boolean = pendingKeys.get(key)?.isNotEmpty() ?: false

    // Indicate this database transaction is a writer of this key.
    private fun addPendingKey(key: K, databaseTransaction: DatabaseTransaction): Boolean {
        var added = true
        pendingKeys.compute(key) { k, oldSet ->
            if (oldSet == null) {
                val newSet = HashSet<DatabaseTransaction>(0)
                newSet += databaseTransaction
                newSet
            } else {
                added = oldSet.add(databaseTransaction)
                oldSet
            }
        }
        return added
    }

    // Remove this database transaction as a writer of this key, because the transaction committed or rolled back.
    private fun removePendingKey(key: K, databaseTransaction: DatabaseTransaction) {
        pendingKeys.compute(key) { k, oldSet ->
            if (oldSet == null) {
                oldSet
            } else {
                oldSet -= databaseTransaction
                if (oldSet.size == 0) null else oldSet
            }
        }
    }

    /**
     * Represents a value in the cache, with transaction isolation semantics.
     *
     * There are 3 states.  Globally missing, globally visible, and being written in a transaction somewhere now or in
     * the past (and it rolled back).
     */
    sealed class Transactional<T> {
        abstract val value: T
        abstract val isPresent: Boolean
        abstract val valueWithoutIsolation: T?

        fun orElse(alt: T?) = if (isPresent) value else alt

        // Everyone can see it, and database transaction committed.
        class Committed<T>(override val value: T) : Transactional<T>() {
            override val isPresent: Boolean
                get() = true
            override val valueWithoutIsolation: T?
                get() = value
        }

        // No one can see it.
        class Missing<T>() : Transactional<T>() {
            override val value: T
                get() = throw NoSuchElementException("Not present")
            override val isPresent: Boolean
                get() = false
            override val valueWithoutIsolation: T?
                get() = null
        }

        // Written in a transaction (uncommitted) somewhere, but there's a small window when this might be seen after commit,
        // hence the committed flag.
        class InFlight<K, T>(private val map: AppendOnlyPersistentMapBase<K, T, *, *>,
                             private val key: K,
                             private val _readerValueLoader: () -> T?,
                             private val _writerValueLoader: () -> T = { throw IllegalAccessException("No value loader provided") }) : Transactional<T>() {

            // A flag to indicate this has now been committed, but hasn't yet been replaced with Committed.  This also
            // de-duplicates writes of the Committed value to the cache.
            private val committed = AtomicBoolean(false)

            // What to do if a non-writer needs to see the value and it hasn't yet been committed to the database.
            // Can be updated into a no-op once evaluated.
            private val readerValueLoader = AtomicReference<() -> T?>(_readerValueLoader)
            // What to do if a writer needs to see the value and it hasn't yet been committed to the database.
            // Can be updated into a no-op once evaluated.
            private val writerValueLoader = AtomicReference<() -> T>(_writerValueLoader)

            fun alsoWrite(_value: T) {
                // Make the lazy loader the writers see actually just return the value that has been set.
                writerValueLoader.set({ _value })
                // We make all these vals so that the lambdas do not need a reference to this, and so the onCommit only has a weak ref to the value.
                // We want this so that the cache could evict the value (due to memory constraints etc) without the onCommit callback
                // retaining what could be a large memory footprint object.
                val tx = contextTransaction
                val strongKey = key
                val weakValue = WeakReference<T>(_value)
                val strongComitted = committed
                val strongMap = map
                if (map.addPendingKey(key, tx)) {
                    // If the transaction commits, update cache to make globally visible if we're first for this key,
                    // and then stop saying the transaction is writing the key.
                    tx.onCommit {
                        if (strongComitted.compareAndSet(false, true)) {
                            val dereferencedKey = strongKey
                            val dereferencedValue = weakValue.get()
                            if (dereferencedValue != null) {
                                strongMap.cache.put(dereferencedKey, Committed(dereferencedValue))
                            }
                        }
                        strongMap.removePendingKey(strongKey, tx)
                    }
                    // If the transaction rolls back, stop saying this transaction is writing the key.
                    tx.onRollback {
                        strongMap.removePendingKey(strongKey, tx)
                    }
                }
            }

            // Lazy load the value a "writer" would see.  If the original loader hasn't been replaced, replace it
            // with one that just returns the value once evaluated.
            private fun loadAsWriter(): T {
                val _value = writerValueLoader.get()()
                if (writerValueLoader.get() == _writerValueLoader) {
                    writerValueLoader.set({ _value })
                }
                return _value
            }

            // Lazy load the value a "reader" would see.  If the original loader hasn't been replaced, replace it
            // with one that just returns the value once evaluated.
            private fun loadAsReader(): T? {
                val _value = readerValueLoader.get()()
                if (readerValueLoader.get() == _readerValueLoader) {
                    readerValueLoader.set({ _value })
                }
                return _value
            }

            // Whether someone reading (only) can see the entry.
            private val isPresentAsReader: Boolean get() = (loadAsReader() != null)
            // Whether the entry is already written and committed, or we are writing it (and thus it can be seen).
            private val isPresentAsWriter: Boolean get() = committed.get() || map.weAreWriting(key)

            override val isPresent: Boolean
                get() = isPresentAsWriter || isPresentAsReader

            // If it is committed or we are writing, reveal the value, potentially lazy loading from the database.
            // If none of the above, see what was already in the database, potentially lazily.
            override val value: T
                get() = if (isPresentAsWriter) loadAsWriter() else if (isPresentAsReader) loadAsReader()!! else throw NoSuchElementException("Not present")

            // The value from the perspective of the eviction algorithm of the cache.  i.e. we want to reveal memory footprint to it etc.
            override val valueWithoutIsolation: T?
                get() = if (writerValueLoader.get() != _writerValueLoader) writerValueLoader.get()() else if (readerValueLoader.get() != _writerValueLoader) readerValueLoader.get()() else null
        }
    }
}

// Open for tests to override
open class AppendOnlyPersistentMap<K, V, E, out EK>(
        toPersistentEntityKey: (K) -> EK,
        fromPersistentEntity: (E) -> Pair<K, V>,
        toPersistentEntity: (key: K, value: V) -> E,
        persistentEntityClass: Class<E>,
        cacheBound: Long = 1024
) : AppendOnlyPersistentMapBase<K, V, E, EK>(
        toPersistentEntityKey,
        fromPersistentEntity,
        toPersistentEntity,
        persistentEntityClass) {
    //TODO determine cacheBound based on entity class later or with node config allowing tuning, or using some heuristic based on heap size
    override val cache = NonInvalidatingCache<K, Transactional<V>>(
            bound = cacheBound,
            loadFunction = { key: K ->
                // This gets called if a value is read and the cache has no Transactional for this key yet.
                val value: V? = loadValue(key)
                if (value == null) {
                    // No visible value
                    if (anyoneWriting(key)) {
                        // If someone is writing (but not us)
                        // For those not writing, the value cannot be seen.
                        // For those writing, they need to re-load the value from the database (which their database transaction CAN see).
                        Transactional.InFlight<K, V>(this, key, { null }, { loadValue(key)!! })
                    } else {
                        // If no one is writing, then the value does not exist.
                        Transactional.Missing<V>()
                    }
                } else {
                    // A value was found
                    if (weAreWriting(key)) {
                        // If we are writing, it might not be globally visible, and was evicted from the cache.
                        // For those not writing, they need to check the database again.
                        // For those writing, they can see the value found.
                        Transactional.InFlight<K, V>(this, key, { loadValue(key) }, { value })
                    } else {
                        // If no one is writing, then make it globally visible.
                        Transactional.Committed<V>(value)
                    }
                }
            })
}

// Same as above, but with weighted values (e.g. memory footprint sensitive).
class WeightBasedAppendOnlyPersistentMap<K, V, E, out EK>(
        toPersistentEntityKey: (K) -> EK,
        fromPersistentEntity: (E) -> Pair<K, V>,
        toPersistentEntity: (key: K, value: V) -> E,
        persistentEntityClass: Class<E>,
        maxWeight: Long,
        weighingFunc: (K, Transactional<V>) -> Int
) : AppendOnlyPersistentMapBase<K, V, E, EK>(
        toPersistentEntityKey,
        fromPersistentEntity,
        toPersistentEntity,
        persistentEntityClass) {
    override val cache = NonInvalidatingWeightBasedCache<K, Transactional<V>>(
            maxWeight = maxWeight,
            weigher = object : Weigher<K, Transactional<V>> {
                override fun weigh(key: K, value: Transactional<V>): Int {
                    return weighingFunc(key, value)
                }
            },
            loadFunction = { key: K ->
                val value: V? = loadValue(key)
                if (value == null) {
                    if (anyoneWriting(key)) {
                        Transactional.InFlight<K, V>(this, key, { null }, { loadValue(key)!! })
                    } else {
                        Transactional.Missing<V>()
                    }
                } else {
                    if (weAreWriting(key)) {
                        Transactional.InFlight<K, V>(this, key, { loadValue(key) }, { value })
                    } else {
                        Transactional.Committed<V>(value)
                    }
                }
            })
}

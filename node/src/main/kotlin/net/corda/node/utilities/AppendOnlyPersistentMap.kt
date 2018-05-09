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
 * Implements a caching layer on top of an *append-only* table accessed via Hibernate mapping. Note that if the same key is [set] twice the
 * behaviour is unpredictable! There is a best-effort check for double inserts, but this should *not* be relied on, so
 * ONLY USE THIS IF YOUR TABLE IS APPEND-ONLY
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
        var isUnique = false
        cache.asMap().compute(key) { _, oldValue ->
            when (oldValue) {
                is Transactional.InFlight<*, V> -> {
                    // TODO: we can do collision detection here and prevent it happening in the database.  But we also have to do deadlock detection, so a bit of work.
                    isUnique = (store(key, value) == null)
                    oldValue.apply { alsoWrite(value) }
                }
                is Transactional.Committed<V> -> oldValue
                else -> {
                    // Null or Missing
                    isUnique = (store(key, value) == null)
                    if (!isUnique && !weAreWriting(key)) {
                        Transactional.Committed(value)
                    } else {
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
     * If the map previously contained a mapping for the key, the old value is not replaced.
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

    protected fun weAreWriting(key: K): Boolean = pendingKeys.get(key)?.contains(contextTransaction) ?: false
    protected fun anyoneWriting(key: K): Boolean = pendingKeys.get(key)?.isNotEmpty() ?: false

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

        // Written in a transaction (uncommitted) somewhere.
        class InFlight<K, T>(private val map: AppendOnlyPersistentMapBase<K, T, *, *>,
                             private val key: K,
                             private val _readerValueLoader: () -> T?,
                             private val _writerValueLoader: () -> T = { throw IllegalAccessException("No value loader provided") }) : Transactional<T>() {
            private val committed = AtomicBoolean(false)
            private val readerValueLoader = AtomicReference<() -> T?>(_readerValueLoader)
            private val writerValueLoader = AtomicReference<() -> T>(_writerValueLoader)

            fun alsoWrite(_value: T) {
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
                    tx.onRollback {
                        strongMap.removePendingKey(strongKey, tx)
                    }
                }
            }

            private fun loadAsWriter(): T {
                val _value = writerValueLoader.get()()
                if (writerValueLoader.get() == _writerValueLoader) {
                    writerValueLoader.set({ _value })
                }
                return _value
            }

            private fun loadAsReader(): T? {
                val _value = readerValueLoader.get()()
                if (readerValueLoader.get() == _readerValueLoader) {
                    readerValueLoader.set({ _value })
                }
                return _value
            }

            private val isPresentAsReader: Boolean get() = (loadAsReader() != null)
            private val isPresentAsWriter: Boolean get() = committed.get() || map.weAreWriting(key)

            override val isPresent: Boolean
                get() = isPresentAsWriter || isPresentAsReader

            override val value: T
                get() = if (isPresentAsWriter) loadAsWriter() else if (isPresentAsReader) loadAsReader()!! else throw NoSuchElementException("Not present")

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

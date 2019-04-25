package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Weigher
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.contextTransaction
import net.corda.nodeapi.internal.persistence.currentDBSession
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty1

/**
 * Implements a caching layer on top of an *append-only* table accessed via Hibernate mapping. Note that if the same key is [set] twice,
 * typically this will result in a duplicate insert if this is racing with another transaction.  The flow framework will then retry.
 *
 * This class relies heavily on the fact that compute operations in the cache are atomic for a particular key.
 */
abstract class AppendOnlyPersistentMapBase<MAP_KEY, MAP_VALUE, PERSISTED_ENTITY, out PERSISTED_ENTITY_KEY>(
        val toPersistentEntityKey: (MAP_KEY) -> PERSISTED_ENTITY_KEY,
        val fromPersistentEntity: (PERSISTED_ENTITY) -> Pair<MAP_KEY, MAP_VALUE>,
        val toPersistentEntity: (key: MAP_KEY, value: MAP_VALUE) -> PERSISTED_ENTITY,
        val persistentEntityClass: Class<PERSISTED_ENTITY>
) {

    private companion object {
        private val log = contextLogger()
    }

    protected abstract val cache: LoadingCache<MAP_KEY, Transactional<MAP_VALUE>>
    protected val pendingKeys = ConcurrentHashMap<MAP_KEY, MutableSet<DatabaseTransaction>>()

    /**
     * Returns the value associated with the key, first loading that value from the storage if necessary.
     */
    operator fun get(key: MAP_KEY): MAP_VALUE? {
        return cache.get(key)!!.orElse(null)
    }

    val size get() = allPersisted().toList().size

    /**
     * Returns all key/value pairs from the underlying storage.
     */
    fun allPersisted(): Sequence<Pair<MAP_KEY, MAP_VALUE>> {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(persistentEntityClass)
        val root = criteriaQuery.from(persistentEntityClass)
        criteriaQuery.select(root)
        val query = session.createQuery(criteriaQuery)
        val result = query.resultList
        return result.map { x -> fromPersistentEntity(x) }.asSequence()
    }

    inline fun <reified T : Comparable<*>> preLoaded(limit: Int? = null, orderingField: KProperty1<PERSISTED_ENTITY, T>? = null, ascending: Boolean = true): AppendOnlyPersistentMapBase<MAP_KEY, MAP_VALUE, PERSISTED_ENTITY, PERSISTED_ENTITY_KEY> {
        val session = currentDBSession()
        val criteriaQuery = session.criteriaBuilder.createQuery(persistentEntityClass)
        val root = criteriaQuery.from(persistentEntityClass)
        criteriaQuery.select(root)
        orderingField?.let {
            if (ascending) {
                criteriaQuery.orderBy(session.criteriaBuilder.asc(root.get<T>(orderingField.name)))
            } else {
                criteriaQuery.orderBy(session.criteriaBuilder.desc(root.get<T>(orderingField.name)))
            }
        }
        val query = session.createQuery(criteriaQuery)
        limit?.let {
            query.maxResults = limit
        }
        val results = query.resultList

        val mappedResults = results.map { fromPersistentEntity(it) }.toMap()
        mappedResults.forEach { this.get(it.key) }
        return this
    }

    private fun set(key: MAP_KEY, value: MAP_VALUE, logWarning: Boolean, store: (MAP_KEY, MAP_VALUE) -> MAP_VALUE?): Boolean {
        // Will be set to true if store says it isn't in the database.
        var isUnique = false
        cache.asMap().compute(key) { _, oldValueInCache ->
            // Always write to the database, unless we can see it's already committed.
            when (oldValueInCache) {
                is Transactional.InFlight<*, MAP_VALUE> -> {
                    // Someone else is writing, so store away!
                    val oldValueInDB = store(key, value)
                    isUnique = (oldValueInDB == null)
                    oldValueInCache.apply { alsoWrite(value) }
                }
                is Transactional.Committed<MAP_VALUE> -> oldValueInCache // The value is already globally visible and cached.  So do nothing since the values are always the same.
                is Transactional.Unknown<*, MAP_VALUE> -> {
                    if (oldValueInCache.isResolved && oldValueInCache.isPresent) {
                        Transactional.Committed(oldValueInCache.value)
                    } else {
                        // Unknown.  Store away!
                        val oldValueInDB = store(key, value)
                        isUnique = (oldValueInDB == null)
                        transactionalForStoreResult(key, value, oldValueInDB)
                    }
                }
                else -> {
                    // Missing or null.  Store away!
                    val oldValueInDB = store(key, value)
                    isUnique = (oldValueInDB == null)
                    transactionalForStoreResult(key, value, oldValueInDB)
                }
            }
        }
        if (logWarning && !isUnique) {
            log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, not inserting the second time")
        }
        return isUnique
    }

    private fun transactionalForStoreResult(key: MAP_KEY, value: MAP_VALUE, oldValue: MAP_VALUE?): Transactional<MAP_VALUE> {
        return if ((oldValue != null) && !weAreWriting(key)) {
            // If we found a value already in the database, and we were not already writing, then it's already committed but got evicted.
            Transactional.Committed(oldValue)
        } else {
            // Some database transactions, including us, writing, with readers seeing whatever is in the database and writers seeing the (in memory) value.
            Transactional.InFlight(this, key, _readerValueLoader = { loadValue(key) }).apply { alsoWrite(value) }
        }
    }

    /**
     * Associates the specified value with the specified key in this map and persists it.
     * If the map previously contained a mapping for the key, the behaviour is unpredictable and may throw an error from the underlying storage.
     */
    operator fun set(key: MAP_KEY, value: MAP_VALUE) =
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
    fun addWithDuplicatesAllowed(key: MAP_KEY, value: MAP_VALUE, logWarning: Boolean = true): Boolean =
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

    fun putAll(entries: Map<MAP_KEY, MAP_VALUE>) {
        entries.forEach {
            set(it.key, it.value)
        }
    }

    private fun loadValue(key: MAP_KEY): MAP_VALUE? {
        val session = currentDBSession()
        val flushing = contextTransaction.flushing
        if (!flushing) {
            // IMPORTANT: The flush is needed because detach() makes the queue of unflushed entries invalid w.r.t. Hibernate internal state if the found entity is unflushed.
            // We want the detach() so that we rely on our cache memory management and don't retain strong references in the Hibernate session.
            session.flush()
        }
        val result = session.find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.apply { if (!flushing) session.detach(result) }?.let(fromPersistentEntity)?.second
    }

    protected fun transactionalLoadValue(key: MAP_KEY): Transactional<MAP_VALUE> {
        // This gets called if a value is read and the cache has no Transactional for this key yet.
        return if (anyoneWriting(key)) {
            // If someone is writing (but not us)
            // For those not writing, they need to re-load the value from the database (which their database transaction MIGHT see).
            // For those writing, they need to re-load the value from the database (which their database transaction CAN see).
            Transactional.InFlight(this, key, { loadValue(key) }, { loadValue(key)!! })
        } else {
            // If no one is writing, then the value may or may not exist in the database.
            Transactional.Unknown(this, key, { loadValue(key) })
        }
    }

    operator fun contains(key: MAP_KEY) = get(key) != null

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
    private fun weAreWriting(key: MAP_KEY): Boolean = pendingKeys[key]?.contains(contextTransaction) ?: false

    private fun anyoneWriting(key: MAP_KEY): Boolean = pendingKeys[key]?.isNotEmpty() ?: false

    // Indicate this database transaction is a writer of this key.
    private fun addPendingKey(key: MAP_KEY, databaseTransaction: DatabaseTransaction): Boolean {
        var added = true
        pendingKeys.compute(key) { _, oldSet ->
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
    private fun removePendingKey(key: MAP_KEY, databaseTransaction: DatabaseTransaction) {
        pendingKeys.compute(key) { _, oldSet ->
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
        abstract val peekableValue: T?

        fun orElse(alt: T?) = if (isPresent) value else alt

        // Everyone can see it, and database transaction committed.
        class Committed<T>(override val value: T) : Transactional<T>() {
            override val isPresent: Boolean
                get() = true
            override val peekableValue: T?
                get() = value
        }

        // No one can see it.
        class Missing<T> : Transactional<T>() {
            override val value: T
                get() = throw NoSuchElementException("Not present")
            override val isPresent: Boolean
                get() = false
            override val peekableValue: T?
                get() = null
        }

        // No one is writing, but we haven't looked in the database yet.  This can only be when there are no writers.
        class Unknown<K, T>(private val map: AppendOnlyPersistentMapBase<K, T, *, *>,
                            private val key: K,
                            private val _valueLoader: () -> T?) : Transactional<T>() {
            override val value: T
                get() = valueWithoutIsolationDelegate.value ?: throw NoSuchElementException("Not present")
            override val isPresent: Boolean
                get() = valueWithoutIsolationDelegate.value != null
            private val valueWithoutIsolationDelegate = lazy(LazyThreadSafetyMode.PUBLICATION) {
                val readValue = _valueLoader()
                // We re-write the value into the cache so that any weigher can re-assess the weight based on the loaded value.
                map.cache.asMap().compute(key) { _, oldValue ->
                    if (oldValue === this@Unknown) {
                        if (readValue == null) Missing() else Committed(readValue)
                    } else oldValue
                }
                readValue
            }
            val isResolved: Boolean get() = valueWithoutIsolationDelegate.isInitialized()
            override val peekableValue: T? get() = if (isResolved && isPresent) value else null
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
                writerValueLoader.set { _value }
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
                            val dereferencedValue = weakValue.get()
                            if (dereferencedValue != null) {
                                strongMap.cache.put(strongKey, Committed(dereferencedValue))
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
                val writerValue = writerValueLoader.get()()
                if (writerValueLoader.get() == _writerValueLoader) {
                    writerValueLoader.set { writerValue }
                }
                return writerValue
            }

            // Lazy load the value a "reader" would see.  If the original loader hasn't been replaced, replace it
            // with one that just returns the value once evaluated.
            private fun loadAsReader(): T? {
                val readerValue = readerValueLoader.get()()
                if (readerValueLoader.get() == _readerValueLoader) {
                    readerValueLoader.set { readerValue }
                }
                return readerValue
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
            override val peekableValue: T?
                get() = if (writerValueLoader.get() != _writerValueLoader) writerValueLoader.get()() else if (readerValueLoader.get() != _writerValueLoader) readerValueLoader.get()() else null
        }
    }
}

// Open for tests to override
open class AppendOnlyPersistentMap<K, V, E, out EK>(
        cacheFactory: NamedCacheFactory,
        name: String,
        toPersistentEntityKey: (K) -> EK,
        fromPersistentEntity: (E) -> Pair<K, V>,
        toPersistentEntity: (key: K, value: V) -> E,
        persistentEntityClass: Class<E>
) : AppendOnlyPersistentMapBase<K, V, E, EK>(
        toPersistentEntityKey,
        fromPersistentEntity,
        toPersistentEntity,
        persistentEntityClass) {
    override val cache = NonInvalidatingCache(
            cacheFactory = cacheFactory,
            name = name,
            loadFunction = { key: K -> transactionalLoadValue(key) })
}

// Same as above, but with weighted values (e.g. memory footprint sensitive).
class WeightBasedAppendOnlyPersistentMap<K, V, E, out EK>(
        cacheFactory: NamedCacheFactory,
        name: String,
        toPersistentEntityKey: (K) -> EK,
        fromPersistentEntity: (E) -> Pair<K, V>,
        toPersistentEntity: (key: K, value: V) -> E,
        persistentEntityClass: Class<E>,
        weighingFunc: (K, Transactional<V>) -> Int
) : AppendOnlyPersistentMapBase<K, V, E, EK>(
        toPersistentEntityKey,
        fromPersistentEntity,
        toPersistentEntity,
        persistentEntityClass) {
    override val cache = NonInvalidatingWeightBasedCache(
            cacheFactory = cacheFactory,
            name = name,
            weigher = Weigher { key, value -> weighingFunc(key, value) },
            loadFunction = { key: K -> transactionalLoadValue(key) })
}

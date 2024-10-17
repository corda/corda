package net.corda.node.utilities

import com.github.benmanes.caffeine.cache.LoadingCache
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.persistence.DatabaseTransaction
import net.corda.nodeapi.internal.persistence.contextTransaction
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.hibernate.Session
import org.hibernate.internal.SessionImpl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Stream

/**
 * Implements a caching layer on top of an *append-only* table accessed via Hibernate mapping. Note that if the same key is [set] twice,
 * typically this will result in a duplicate insert if this is racing with another transaction.  The flow framework will then retry.
 *
 * This class relies heavily on the fact that compute operations in the cache are atomic for a particular key.
 */
@Suppress("TooManyFunctions")
abstract class AppendOnlyPersistentMapBase<K : Any, V, E, out EK>(
        val toPersistentEntityKey: (K) -> EK,
        val fromPersistentEntity: (E) -> Pair<K, V>,
        val toPersistentEntity: (key: K, value: V) -> E,
        val persistentEntityClass: Class<E>
) {

    private companion object {
        private val log = contextLogger()
    }

    protected class PendingKeyValue(val transactions: MutableSet<DatabaseTransaction>, val estimatedSize: Int)

    protected abstract val cache: LoadingCache<K, Transactional<V>>
    protected val pendingKeys = ConcurrentHashMap<K, PendingKeyValue>()

    /**
     * Returns the value associated with the key, first loading that value from the storage if necessary.
     */
    operator fun get(key: K): V? = cache.get(key)?.orElse(null)

    val size: Long get() = allPersisted.use { it.count() }

    /**
     * Returns all key/value pairs from the underlying storage in a [Stream].
     *
     * Make sure to close the [Stream] once it's been processed.
     */
    val allPersisted: Stream<Pair<K, V>>
        get() {
            val session = currentDBSession()
            val criteriaQuery = session.criteriaBuilder.createQuery(persistentEntityClass)
            val root = criteriaQuery.from(persistentEntityClass)
            criteriaQuery.select(root)
            val query = session.createQuery(criteriaQuery)
            return query.stream().map(fromPersistentEntity)
        }

    private fun set(key: K, value: V, logWarning: Boolean, store: (K, V) -> V?): Boolean {
        // Will be set to true if store says it isn't in the database.
        var wasWritten = false
        cache.asMap().compute(key) { _, oldValueInCache ->
            // Always write to the database, unless we can see it's already committed.
            when (oldValueInCache) {
                is Transactional.InFlight<*, V> -> {
                    // Someone else is writing, so store away!
                    val retainedValueFromDB = store(key, value)
                    wasWritten = (retainedValueFromDB == null)
                    // If the store function claims the value is new in the DB, then either the value is brand new or updated. In this case,
                    // update the old value in the cache with the new value. Otherwise, leave it as it was before.
                    if (wasWritten) {
                        oldValueInCache.apply { alsoWrite(value) }
                    } else {
                        oldValueInCache
                    }
                }
                is Transactional.Committed<V> -> if (oldValueInCache.value == value) {
                    oldValueInCache // The value is already globally visible and cached.  So do nothing since the values are always the same.
                } else {
                    val retainedValueFromDB = store(key, value)
                    wasWritten = (retainedValueFromDB == null)
                    // If the value has been updated, then isUnique will be true. In this case, the Committed value needs to be replaced
                    // with InFlight to indicate that this transaction has changed the value associated with this key. Note that this allows
                    // for cases where the value passed to set differs from that in the cache, but an update function has decided that this
                    // differing value should not be written to the database.
                    if (wasWritten) {
                        Transactional.InFlight(this, key, weight = weight(key, value), _readerValueLoader = { loadValue(key) })
                                .apply { alsoWrite(value) }
                    } else {
                        oldValueInCache
                    }
                }
                is Transactional.Unknown<*, V> -> {
                    if (oldValueInCache.isResolved && oldValueInCache.isPresent) {
                        Transactional.Committed(oldValueInCache.value)
                    } else {
                        // Unknown.  Store away!
                        val retainedValueInDB = store(key, value)
                        wasWritten = (retainedValueInDB == null)
                        transactionalForStoreResult(key, value, retainedValueInDB)
                    }
                }
                else -> {
                    // Missing or null.  Store away!
                    val retainedValueInDB = store(key, value)
                    wasWritten = (retainedValueInDB == null)
                    transactionalForStoreResult(key, value, retainedValueInDB)
                }
            }
        }
        if (logWarning && !wasWritten) {
            log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, not inserting the second time")
        }
        return wasWritten
    }

    private fun transactionalForStoreResult(key: K, value: V, oldValue: V?): Transactional<V> {
        return if ( (oldValue != null) && !weAreWriting(key)) {
            // If we found a value already in the database, and we were not already writing, then it's already committed but got evicted.
            Transactional.Committed(oldValue)
        } else {
            // Some database transactions, including us, writing, with readers seeing whatever is in the database and writers seeing the (in memory) value.
            Transactional.InFlight(this, key, weight = weight(key, value), _readerValueLoader = { loadValue(key) })
                    .apply { alsoWrite(value) }
        }
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
     * An optional [forceUpdate] function allows performing additional checks/updates on an existingEntry to determine whether the map
     * should be updated.
     * @return true if added key was unique, otherwise false
     */
    fun addWithDuplicatesAllowed(key: K, value: V, logWarning: Boolean = true,
                                 forceUpdate: (K, V, E) -> Boolean = { _, _, _ -> false }): Boolean {
        return set(key, value, logWarning) { k, v ->
            val session = currentDBSession()
            val existingEntry = session.find(persistentEntityClass, toPersistentEntityKey(k))
            if (existingEntry == null) {
                session.save(toPersistentEntity(k, v))
                null
            }
            else if (!forceUpdate(key, value, existingEntry)) {
                fromPersistentEntity(existingEntry).second
            } else null
        }
    }

    /**
     * Associates the specified value with the specified key in this map and persists it.
     * This function will first attempt to update the value currently in the map using the update function. If the update function returns
     * true, this indicate that the persisted value has been updated and the corresponding cached value should also be updated. If it
     * returns false, then this function attempts to add the value to the database, taking into account the possibility that the value
     * already exists but an update is undesirable.
     *
     * @param key The key to associate the value with
     * @param value The value to associate to the key
     * @param updateFn A function to calculate an update to the persisted value. This should return true if the persisted value has been
     *                 updated, and false if it has not or the value is not currently present in the database.
     * @return true if the value was added or updated, false otherwise.
     */
    fun addOrUpdate(key: K, value: V, updateFn: (K, V) -> Boolean): Boolean {
        return set(key, value, logWarning = false) { k, v ->
            val updated = updateFn(k, v)
            if (updated) {
                // This needs to be null to ensure that set returns true when a value is updated.
                null
            } else {
                val session = currentDBSession()
                val existingEntry = session.find(persistentEntityClass, toPersistentEntityKey(k))
                if (existingEntry == null) {
                    session.save(toPersistentEntity(k, v))
                    null
                } else {
                    fromPersistentEntity(existingEntry).second
                }
            }
        }
    }

    fun putAll(entries: Map<K, V>) {
        entries.forEach {
            set(it.key, it.value)
        }
    }

    private fun loadValue(key: K): V? {
        val session = currentDBSession()
        val isSafeToDetach = isSafeToFlushAndDetach(session)
        if (isSafeToDetach) {
            // IMPORTANT: The flush is needed because detach() makes the queue of unflushed entries invalid w.r.t. Hibernate internal state if the found entity is unflushed.
            // We want the detach() so that we rely on our cache memory management and don't retain strong references in the Hibernate session.
            session.flush()
        }
        val result = session.find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.apply { if (isSafeToDetach) session.detach(result) }?.let(fromPersistentEntity)?.second
    }

    private fun isSafeToFlushAndDetach(session: Session): Boolean {
        if (session !is SessionImpl)
            return true

        val flushInProgress = session.persistenceContext.isFlushing
        val cascadeInProgress = session.persistenceContext.cascadeLevel > 0
        return !flushInProgress && !cascadeInProgress
    }

    protected fun transactionalLoadValue(key: K): Transactional<V> {
        // This gets called if a value is read and the cache has no Transactional for this key yet.
        val estimatedSize = anyoneWriting(key)
        return if (estimatedSize != -1) {
            // If someone is writing (but not us)
            // For those not writing, they need to re-load the value from the database (which their database transaction MIGHT see).
            // For those writing, they need to re-load the value from the database (which their database transaction CAN see).
            Transactional.InFlight(this, key, estimatedSize, { loadValue(key) }, { loadValue(key)!! })
        } else {
            // If no one is writing, then the value may or may not exist in the database.
            Transactional.Unknown(this, key) { loadValue(key) }
        }
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

    fun clear(id: K) = cache.invalidate(id)

    // Helpers to know if transaction(s) are currently writing the given key.
    private fun weAreWriting(key: K): Boolean = pendingKeys[key]?.transactions?.contains(contextTransaction) ?: false

    private fun anyoneWriting(key: K): Int = pendingKeys[key]?.estimatedSize ?: -1

    protected open fun weight(key: K, value: V): Int = 1

    // Indicate this database transaction is a writer of this key.
    private fun addPendingKey(key: K, databaseTransaction: DatabaseTransaction, estimatedSize: Int): Boolean {
        var added = true
        pendingKeys.compute(key) { _, value: PendingKeyValue? ->
            val oldSet = value?.transactions
            if (oldSet == null) {
                val newSet = HashSet<DatabaseTransaction>(0)
                newSet += databaseTransaction
                PendingKeyValue(newSet, estimatedSize)
            } else {
                added = oldSet.add(databaseTransaction)
                value
            }
        }
        return added
    }

    // Remove this database transaction as a writer of this key, because the transaction committed or rolled back.
    private fun removePendingKey(key: K, databaseTransaction: DatabaseTransaction) {
        pendingKeys.compute(key) { _, value: PendingKeyValue? ->
            val oldSet = value?.transactions
            if (oldSet == null) {
                null
            } else {
                oldSet -= databaseTransaction
                if (oldSet.size == 0) null else value
            }
        }
    }

    /**
     * Represents a value in the cache, with transaction isolation semantics.
     *
     * There are 3 states.  Globally missing, globally visible, and being written in a transaction somewhere now or in
     * the past (and it rolled back).
     */
    @Suppress("MagicNumber")
    sealed class Transactional<T> {
        abstract val value: T
        abstract val isPresent: Boolean
        abstract val peekableValue: T?
        abstract val shallowSize: Int

        fun orElse(alt: T?) = if (isPresent) value else alt

        // Everyone can see it, and database transaction committed.
        class Committed<T>(override val value: T) : Transactional<T>() {
            override val isPresent: Boolean
                get() = true
            override val peekableValue: T?
                get() = value
            override val shallowSize: Int
                get() = 48
        }

        // No one can see it.
        class Missing<T> : Transactional<T>() {
            override val value: T
                get() = throw NoSuchElementException("Not present")
            override val isPresent: Boolean
                get() = false
            override val peekableValue: T?
                get() = null
            override val shallowSize: Int
                get() = 16
        }

        // No one is writing, but we haven't looked in the database yet.  This can only be when there are no writers.
        class Unknown<K : Any, T>(private val map: AppendOnlyPersistentMapBase<K, T, *, *>,
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
            override val shallowSize: Int
                get() = 128
        }

        // Written in a transaction (uncommitted) somewhere, but there's a small window when this might be seen after commit,
        // hence the committed flag.
        class InFlight<K : Any, T>(private val map: AppendOnlyPersistentMapBase<K, T, *, *>,
                                   private val key: K,
                                   val weight: Int,
                                   private val _readerValueLoader: () -> T?,
                                   private val _writerValueLoader: () -> T = { throw IllegalAccessException("No value loader provided") }) : Transactional<T>() {

            // A flag to indicate this has now been committed, but hasn't yet been replaced with Committed.  This also
            // de-duplicates writes of the Committed value to the cache.
            private val committed = AtomicBoolean(false)

            // What to do if a non-writer needs to see the value and it hasn't yet been committed to the database.
            // Can be updated into a no-op once evaluated.
            private val readerValueLoader = AtomicReference(_readerValueLoader)
            // What to do if a writer needs to see the value and it hasn't yet been committed to the database.
            // Can be updated into a no-op once evaluated.
            private val writerValueLoader = AtomicReference(_writerValueLoader)

            fun alsoWrite(_value: T) {
                // Make the lazy loader the writers see actually just return the value that has been set.
                writerValueLoader.set { _value }
                // We make all these vals so that the lambdas do not need a reference to this, and so the onCommit only has a weak ref to the value.
                // We want this so that the cache could evict the value (due to memory constraints etc) without the onCommit callback
                // retaining what could be a large memory footprint object.
                val tx = contextTransaction
                val strongKey = key
                val strongMap = map
                if (map.addPendingKey(key, tx, weight)) {
                    // If the transaction commits, update cache to make globally visible if we're first for this key,
                    // and then stop saying the transaction is writing the key.
                    tx.onCommit {
                        strongMap.cache.asMap().computeIfPresent(strongKey) { _, transactional: Transactional<T> ->
                            if (transactional is InFlight<*, T>) {
                                transactional.committed.set(true)
                                val value = transactional.peekableValue
                                if (value != null) {
                                    Committed(value)
                                } else {
                                    transactional
                                }
                            } else {
                                transactional
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
                get() = if (writerValueLoader.get() != _writerValueLoader) writerValueLoader.get()() else if (readerValueLoader.get() != _readerValueLoader) readerValueLoader.get()() else null

            override val shallowSize: Int
                get() = 256
        }
    }
}

// Open for tests to override
open class AppendOnlyPersistentMap<K : Any, V, E, out EK>(
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
class WeightBasedAppendOnlyPersistentMap<K : Any, V, E, out EK>(
        cacheFactory: NamedCacheFactory,
        name: String,
        toPersistentEntityKey: (K) -> EK,
        fromPersistentEntity: (E) -> Pair<K, V>,
        toPersistentEntity: (key: K, value: V) -> E,
        persistentEntityClass: Class<E>,
        private val weighingFunc: (K, V?) -> Int
) : AppendOnlyPersistentMapBase<K, V, E, EK>(
        toPersistentEntityKey,
        fromPersistentEntity,
        toPersistentEntity,
        persistentEntityClass) {

    override fun weight(key: K, value: V): Int = weighingFunc(key, value)

    override val cache = NonInvalidatingWeightBasedCache(
            cacheFactory = cacheFactory,
            name = name,
            weigher = { key, value: Transactional<V> ->
                value.shallowSize + if (value is Transactional.InFlight<*, *>) {
                    value.weight * 2
                } else {
                    weighingFunc(key, value.peekableValue)
                }
            },
            loadFunction = { key: K -> transactionalLoadValue(key) })
}

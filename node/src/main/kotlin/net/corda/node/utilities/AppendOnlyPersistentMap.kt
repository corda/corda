package net.corda.node.utilities

import net.corda.core.utilities.loggerFor
import java.util.*


/**
 * Implements a caching layer on top of an *append-only* table accessed via Hibernate mapping. Note that if the same key is [put] twice the
 * behaviour is unpredictable! There is a best-effort check for double inserts, but this should *not* be relied on, so
 * ONLY USE THIS IF YOUR TABLE IS APPEND-ONLY
 */
class AppendOnlyPersistentMap<K, V, E, EK> (
        val toPersistentEntityKey: (K) -> EK,
        val fromPersistentEntity: (E) -> Pair<K,V>,
        val toPersistentEntity: (key: K, value: V) -> E,
        val persistentEntityClass: Class<E>,
        cacheBound: Long = 1024
) { //TODO determine cacheBound based on entity class later or with node config allowing tuning, or using some heuristic based on heap size

    private companion object {
        val log = loggerFor<AppendOnlyPersistentMap<*, *, *, *>>()
    }

    private val cache = NonInvalidatingCache<K, Optional<V>>(
            bound = cacheBound,
            concurrencyLevel = 8,
            loadFunction = { key -> Optional.ofNullable(loadValue(key)) }
    )

    operator fun get(key: K): V? {
        return cache.get(key).orElse(null)
    }

    fun allPersisted(): Sequence<Pair<K, V>> {
        val criteriaQuery = DatabaseTransactionManager.current().session.criteriaBuilder.createQuery(persistentEntityClass)
        val root = criteriaQuery.from(persistentEntityClass)
        criteriaQuery.select(root)
        val query = DatabaseTransactionManager.current().session.createQuery(criteriaQuery)
        val result = query.resultList
        return result.map { x -> fromPersistentEntity(x) }.asSequence()
    }

    private tailrec fun set(key: K, value: V, logWarning: Boolean = true, store: (K,V) -> Boolean) : Boolean {
        var inserted = false
        var uniqueInDb = true
        val existingInCache = cache.get(key) { //thread safe, if multiple threads may wait until the first one has loaded
            inserted = true
            // Key wasn't in the cache and might not be in DB.
            // Depending on 'store' method, this may insert without checking key duplication or it may avoid inserting a duplicated key.
            uniqueInDb = store(key, value)
            Optional.of(value)
        }
        if (!inserted) {
            if (existingInCache.isPresent) {
                // Key already exists in cache, do nothing.
                uniqueInDb = false
            } else {
                // This happens when the key was queried before with no value associated. We invalidate the cached null
                // value and recursively call set again. This is to avoid race conditions where another thread queries after
                // the invalidate but before the set.
                cache.invalidate(key)
                return set(key, value, logWarning, store)
            }
        }
        if (logWarning && !uniqueInDb) {
            log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, not inserting the second time")
        }
        return uniqueInDb
    }

    /**
     * Puts the value into the map and caches it.
     */
    operator fun set(key: K, value: V) {
        set(key, value, logWarning = false) {
            key,value -> DatabaseTransactionManager.current().session.save(toPersistentEntity(key,value))
            true
        }
    }

    /**
     * Puts the value or replace existing one in the map and caches it.
     * @return true if added key was unique, otherwise false
     */
    fun addWithDuplicatesAllowed(key: K, value: V): Boolean =
            set(key, value) {
                key, value ->
                val prev = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
                if (prev == null) {
                    DatabaseTransactionManager.current().session.save(toPersistentEntity(key,value))
                    true
                } else {
                    false
                }
            }

    private fun loadValue(key: K): V? {
        val result = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.let(fromPersistentEntity)?.second
    }

}


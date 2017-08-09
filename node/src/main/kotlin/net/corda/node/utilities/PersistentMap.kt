package net.corda.node.utilities

import net.corda.core.utilities.loggerFor
import java.util.*


/**
 * Implements a caching layer on top of a table accessed via Hibernate mapping.
 */
class PersistentMap<K, V, E, EK> (
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


    private tailrec fun set(key: K, value: V, store: (K,V) -> Boolean) : Boolean {
        var inserted = false
        var uniqueInDb = true
        val existingInCache = cache.get(key) { //thread safe, if multiple threads may wait until the first one has loaded
            inserted = true
            // Value wasn't in the cache and might not be in DB.
            // Store the value, depending on store implementation this may overwrite existing entry in DB.
            uniqueInDb = store(key, value)
            Optional.of(value)
        }
        if (!inserted) {
            if (existingInCache.isPresent) {
                // Value was cached already, store the new value in the DB (depends on tore implementation) and refresh cache.
                synchronized(this) {
                    cache.put(key, Optional.of(value))
                    uniqueInDb = store(key, value)
                }
                log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, not inserting the second time")
            } else {
                // This happens when the key was queried before with no value associated. We invalidate the cached null
                // value and recursively call set again. This is to avoid race conditions where another thread queries after
                // the invalidate but before the set.
                cache.invalidate(key)
                return set(key, value, store)
            }
        }
        if(!uniqueInDb) {
            log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, value is overwritten")
        }
        return uniqueInDb
    }

    /**
     * Puts the value into the map and caches it.
     */
    operator fun set(key: K, value: V) {
        set(key, value) {
            key,value ->  DatabaseTransactionManager.current().session.save(toPersistentEntity(key,value))
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
            if (prev != null) {
                DatabaseTransactionManager.current().session.merge(toPersistentEntity(key,value))
                false
            } else {
                DatabaseTransactionManager.current().session.save(toPersistentEntity(key,value))
                true
            }
        }


    private fun loadValue(key: K): V? {
        val result = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.let(fromPersistentEntity)?.second
    }


    //TODO add remove method required for a service
}

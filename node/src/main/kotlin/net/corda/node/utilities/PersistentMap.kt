package net.corda.node.utilities


import net.corda.core.utilities.loggerFor
import java.util.*


/**
 * Implements a caching layer on top of a table accessed via Hibernate mapping. Note that if the same key is [put] twice the
 * the value is overwritten.
 */
class PersistentMap<K, V, E, EK> (
        val cacheBound: Long,     //TODO to determine the bound based on entity class later or with node config allowing tuning, or using some heuristic based on heap size
        val toPersistentEntityKey: (K) -> EK,
        val fromPersistentEntity: (E) -> Pair<K,V>,
        val toPersistentEntity: (key: K, value: V) -> E,
        val persistentEntityClass: Class<E>
) {
    private companion object {
        val log = loggerFor<AppendOnlyPersistentMap<*, *, *, *>>()
    }

    private val cache = NonInvalidatingCache<K, Optional<V>>(
            bound = cacheBound,
            concurrencyLevel = 8,
            loadFunction = { key -> Optional.ofNullable(loadValue(key)) }
    )

    operator fun get(key: K): V? {
        synchronized(this) {
            return cache.get(key).orElse(null)
        }
    }

    fun remove(key: K): V? {
        synchronized(this) {
            val result = cache.get(key).orElse(null)
            cache.invalidate(key)
            val session = DatabaseTransactionManager.current().session
            val elem = session.find(persistentEntityClass, toPersistentEntityKey(key))
            if (elem != null) {
                session.remove(elem) //TODO removal listener
                return fromPersistentEntity(elem).second
            }
            return result
        }
    }

    fun allPersisted(): Sequence<Pair<K, V>> {
        synchronized(this) {
            val criteriaQuery = DatabaseTransactionManager.current().session.criteriaBuilder.createQuery(persistentEntityClass)
            val root = criteriaQuery.from(persistentEntityClass)
            criteriaQuery.select(root)
            val query = DatabaseTransactionManager.current().session.createQuery(criteriaQuery)
            val result = query.resultList
            return result.map { x -> fromPersistentEntity(x) }.asSequence()
        }
    }

    /**
     * Puts the value into the map and caches it.
     */
    operator tailrec fun set(key: K, value: V) {
        synchronized(this) {
            var inserted = false
            val existing = cache.get(key) {
                //thread safe, if multiple threads may wait until the first one has loaded
                inserted = true
                // Store the value. Note that if the key-value pair is already in the DB
                // but was evicted from the cache then this operation will overwrite the entry in the DB!
                storeValue(key, value)
                Optional.of(value)
            }
            if (!inserted) {
                // Value was inserted into cache fine, store the value. Note that if the key-value pair is already in the DB
                // but was evicted from the cache then this operation will overwrite the entry in the DB!
                ///storeValue(key, value)
                if (existing.isPresent) {
                    // An existing value is cached, in this case we know for sure that there is a problem.
                    log.warn("Double insert detected in AppendOnlyJDBCMap for entity class $persistentEntityClass key $key, not inserting the second time")
                } else {
                    // This happens when the key was queried before with no value associated. We invalidate the cached null
                    // value and recursively call set again. This is to avoid race conditions where another thread queries after
                    // the invalidate but before the set.
                    cache.invalidate(key)
                    set(key, value)
                }
            }
        }
    }

    private fun loadValue(key: K): V? {
        val result = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.let(fromPersistentEntity)?.second
    }

    private fun storeValue(key: K, value: V) {
        //this is invoked only by cache.get and it guaranteed that database didn't have a previous row with the same key
        val entity = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
        if (entity==null) {
            DatabaseTransactionManager.current().session.save(toPersistentEntity(key, value))
        } else {
            DatabaseTransactionManager.current().session.merge(toPersistentEntity(key, value))
        }
    }
}

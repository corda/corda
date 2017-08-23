package net.corda.node.utilities


import net.corda.core.utilities.loggerFor
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


/**
 * Implements an unbound caching layer on top of a table accessed via Hibernate mapping.
 * The cache can be cleared in bull operation which removed entries in underlying table.
 */
class AppendOnlyClearablePersistentMap<K, V, E, out EK> (
        val toPersistentEntityKey: (K) -> EK,
        val fromPersistentEntity: (E) -> Pair<K,V>,
        val toPersistentEntity: (key: K, value: V) -> E,
        val persistentEntityClass: Class<E>
) {
    private companion object {
        val log = loggerFor<AppendOnlyClearablePersistentMap<*, *, *, *>>()
    }

    private val cache = NonInvalidatingUnboundCache<K,Optional<V>>(
            concurrencyLevel = 8,
            loadFunction = { key -> Optional.ofNullable(loadValue(key)) },
            keysToPreload = { -> loadAllKeys() }
    )

    private val lock = ReentrantReadWriteLock()

    operator fun get(key: K): V? {
        lock.read {
            return cache.get(key).orElse(null)
        }
    }

    private class Entry<out K, out V>(override val key: K, override val value: V) : Map.Entry<K, V>

    fun all(): Sequence<Map.Entry<K, V>> {
        lock.read {
            return cache.asMap().asSequence().map { Entry(it.key, it.value.get()) }
        }
    }

    val size = cache.size().toInt()

    private tailrec fun set(key: K, value: V, logWarning: Boolean = true, store: (K,V) -> V?, replace: (K, V) -> Unit) : Boolean {
        var insertionAttempt = false
        var isUnique = true
        val existingInCache = cache.get(key) { // Thread safe, if multiple threads may wait until the first one has loaded.
            insertionAttempt = true
            // Value wasn't in the cache and wasn't in DB (because the cache is unbound).
            // Store the value, depending on store implementation this may replace existing entry in DB.
            store(key, value)
            Optional.of(value)
        }
        if (!insertionAttempt) {
            if (existingInCache.isPresent) {
                // Key already exists in cache, store the new value in the DB (depends on tore implementation) and refresh cache.
                isUnique = false
                replace(key, value)
            } else {
                // This happens when the key was queried before with no value associated. We invalidate the cached null
                // value and recursively call set again. This is to avoid race conditions where another thread queries after
                // the invalidate but before the set.
                cache.invalidate(key)
                return set(key, value, logWarning, store, replace)
            }
        }
        if (logWarning && !isUnique) {
            log.warn("Double insert in ${this.javaClass.name} for entity class $persistentEntityClass key $key, not inserting the second time")
        }
        return isUnique
    }

    /**
     * Associates the specified value with the specified key in this map and persists it.
     * WARNING! If the map previously contained a mapping for the key, the behaviour is unpredictable and may throw an error from the underlying storage.
     */
    operator fun set(key: K, value: V) =
            lock.read { // the read lock prevents when the whole cache is being cleared, set is internally synchronized by each key inside Guava cache
                set(key, value,
                    logWarning = false,
                    store = { k: K, v: V ->
                        DatabaseTransactionManager.current().session.save(toPersistentEntity(k, v))
                        null
                    },
                    replace = { _: K, _: V -> Unit }
                )
            }

    fun putAll(entries: Map<K,V>) {
        lock.read { // see comment inside set public function
            entries.forEach {
                set(it.key, it.value)
            }
        }
    }

    fun clear() {
        val session = DatabaseTransactionManager.current().session
        val deleteQuery = session.criteriaBuilder.createCriteriaDelete(persistentEntityClass)
        deleteQuery.from(persistentEntityClass)
        lock.write {
            session.createQuery(deleteQuery).executeUpdate()
            cache.invalidateAll()
        }
    }

    private fun loadValue(key: K): V? {
        val result = DatabaseTransactionManager.current().session.find(persistentEntityClass, toPersistentEntityKey(key))
        return result?.let(fromPersistentEntity)?.second
    }

    private fun loadAllKeys() : Iterable<K> {
        val session = DatabaseTransactionManager.current().session
        val criteriaQuery = session.criteriaBuilder.createQuery(persistentEntityClass)
        criteriaQuery.select(criteriaQuery.from(persistentEntityClass))
        return session.createQuery(criteriaQuery).resultList.map { e -> fromPersistentEntity(e as E).first }.asIterable()
    }
}

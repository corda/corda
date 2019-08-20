package net.corda.node.services.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.crypto.toStringShort
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.nodeapi.internal.KeyOwningIdentity
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.security.PublicKey
import java.util.*

/**
 * The [PublicKeyToOwningIdentityCacheImpl] provides a caching layer over the pk_hash_to_external_id table. Gets will attempt to read an
 * external identity from the database if it is not present in memory, while sets will write external identity UUIDs to this database table.
 */
class PublicKeyToOwningIdentityCacheImpl(private val database: CordaPersistence,
                                         cacheFactory: NamedCacheFactory) : WritablePublicKeyToOwningIdentityCache {
    companion object {
        val log = contextLogger()
    }

    private val cache = cacheFactory.buildNamed<PublicKey, KeyOwningIdentity>(Caffeine.newBuilder(), "PublicKeyToOwningIdentityCache_cache")

    private fun isKeyBelongingToNode(key: PublicKey): Boolean {
        return database.transaction {
            val criteriaBuilder = session.criteriaBuilder
            val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
            val queryRoot = criteriaQuery.from(BasicHSMKeyManagementService.PersistentKey::class.java)
            criteriaQuery.select(criteriaBuilder.count(queryRoot))
            criteriaQuery.where(
                    criteriaBuilder.equal(queryRoot.get<String>(BasicHSMKeyManagementService.PersistentKey::publicKeyHash.name), key.toStringShort())
            )
            val query = session.createQuery(criteriaQuery)
            query.uniqueResult() > 0
        }
    }

    /**
     * Return the owning identity associated with a given key.
     *
     * This method caches the result of a database lookup to prevent multiple database accesses for the same key. This assumes that once a
     * key is generated, the UUID assigned to it is never changed.
     */
    override operator fun get(key: PublicKey): KeyOwningIdentity? {
        return cache.asMap().computeIfAbsent(key) {
            database.transaction {
                val criteriaBuilder = session.criteriaBuilder
                val criteriaQuery = criteriaBuilder.createQuery(UUID::class.java)
                val queryRoot = criteriaQuery.from(PublicKeyHashToExternalId::class.java)
                criteriaQuery.select(queryRoot.get(PublicKeyHashToExternalId::externalId.name))
                criteriaQuery.where(
                        criteriaBuilder.equal(queryRoot.get<String>(PublicKeyHashToExternalId::publicKeyHash.name), key.toStringShort())
                )
                val query = session.createQuery(criteriaQuery)
                val uuid = query.uniqueResult()
                if (uuid != null || isKeyBelongingToNode(key)) {
                    val signingEntity = KeyOwningIdentity.fromUUID(uuid)
                    log.debug { "Database lookup for public key ${key.toStringShort()}, found signing entity $signingEntity" }
                    signingEntity
                } else {
                    log.debug { "Attempted to find owning identity for public key ${key.toStringShort()}, but key is unknown to node" }
                    null
                }
            }
        }
    }

    /**
     * Associate a key with a given [KeyOwningIdentity].
     *
     * This will write to the pk_hash_to_external_id table if the key belongs to an external id. If a key is created within a transaction
     * that is rolled back in the future, the cache may contain stale entries. However, that key should be missing from the
     * KeyManagementService in that case, and so querying it from this cache should not occur (as the key is inaccessible).
     *
     * The same key should not be written twice.
     */
    override operator fun set(key: PublicKey, value: KeyOwningIdentity) {
        when (value) {
            is KeyOwningIdentity.MappedIdentity -> {
                database.transaction { session.persist(PublicKeyHashToExternalId(value.uuid, key)) }
            }
            is KeyOwningIdentity.UnmappedIdentity -> {
            }
        }
        cache.asMap()[key] = value
    }
}
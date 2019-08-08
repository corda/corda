package net.corda.node.services.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.crypto.toStringShort
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.KeyOwningIdentity
import java.security.PublicKey
import java.util.*
import javax.persistence.NoResultException
import javax.persistence.criteria.CriteriaBuilder

/**
 * The [PublicKeyToOwningIdentityCacheImpl] provides a caching layer over the pk_hash_to_external_id table. Gets will attempt to read an
 * external identity from the database if it is not present in memory, while sets will write external identity UUIDs to this database table.
 */
class PublicKeyToOwningIdentityCacheImpl(private val database: CordaPersistence,
                                         cacheFactory: NamedCacheFactory) : WritablePKToOwningIDCache {
    companion object {
        val log = contextLogger()
    }

    private val criteriaBuilder: CriteriaBuilder by lazy { database.hibernateConfig.sessionFactoryForRegisteredSchemas.criteriaBuilder }

    private val cache = cacheFactory.buildNamed<PublicKey, KeyOwningIdentity>(Caffeine.newBuilder(), "PublicKeyToOwningIdentityCache_cache")

    /**
     * Return the owning identity associated with a given key.
     *
     * This method caches the result of a database lookup to prevent multiple database accesses for the same key. This assumes that once a
     * key is generated, the UUID assigned to it is never changed.
     */
    override operator fun get(key: PublicKey): KeyOwningIdentity {
        return cache.asMap().computeIfAbsent(key) {
            database.transaction {
                val criteriaQuery = criteriaBuilder.createQuery(UUID::class.java)
                val queryRoot = criteriaQuery.from(PublicKeyHashToExternalId::class.java)
                criteriaQuery.select(queryRoot.get(PublicKeyHashToExternalId::externalId.name))
                criteriaQuery.where(
                        criteriaBuilder.equal(queryRoot.get<String>(PublicKeyHashToExternalId::publicKeyHash.name), key.toStringShort())
                )
                val query = session.createQuery(criteriaQuery)

                // If no entry exists for the queried key, treat the result as null.
                val signingEntity = try {
                    KeyOwningIdentity.fromUUID(query.singleResult)
                } catch (e: NoResultException) {
                    KeyOwningIdentity.fromUUID(null)
                }

                log.debug { "Database lookup for public key ${key.toStringShort()}, found signing entity $signingEntity" }
                signingEntity
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
            is KeyOwningIdentity.ExternalIdentity -> {
                database.transaction { session.persist(PublicKeyHashToExternalId(value.uuid, key)) }
            }
            is KeyOwningIdentity.NodeIdentity -> {}
        }
        cache.asMap()[key] = value
    }
}
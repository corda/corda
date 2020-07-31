package net.corda.node.services.persistence

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.crypto.toStringShort
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.KeyOwningIdentity
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.security.PublicKey

/**
 * The [PublicKeyToOwningIdentityCacheImpl] provides a caching layer over the pk_hash_to_external_id table. Gets will attempt to read an
 * external identity from the database if it is not present in memory, while sets will write external identity UUIDs to this database table.
 */
class PublicKeyToOwningIdentityCacheImpl(private val database: CordaPersistence,
                                         cacheFactory: NamedCacheFactory) : WritablePublicKeyToOwningIdentityCache {
    companion object {
        val log = contextLogger()
    }

    private val cache = cacheFactory.buildNamed<PublicKey, KeyOwningIdentity>(
            Caffeine.newBuilder(),
            "PublicKeyToOwningIdentityCache_cache"
    )

    /**
     * Return the owning identity associated with a given key.
     *
     * This method caches the result of a database lookup to prevent multiple database accesses for the same key. This assumes that once a
     * key is generated, the UUID assigned to it is never changed.
     */
    override operator fun get(key: PublicKey): KeyOwningIdentity {
        return cache.asMap().computeIfAbsent(key) {
            database.transaction {
                val uuid = session.find(PublicKeyHashToExternalId::class.java, key.toStringShort())?.externalId
                if (uuid != null) {
                    val signingEntity = KeyOwningIdentity.fromUUID(uuid)
                    log.debug { "Database lookup for public key ${key.toStringShort()}, found signing entity $signingEntity" }
                    signingEntity
                } else {
                    log.debug { "Database lookup for public key  ${key.toStringShort()}, using ${KeyOwningIdentity.UnmappedIdentity}" }
                    KeyOwningIdentity.UnmappedIdentity
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
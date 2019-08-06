package net.corda.nodeapi.internal.utilities

import net.corda.core.crypto.toStringShort
import net.corda.core.internal.createSimpleCache
import net.corda.core.internal.toSynchronised
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.security.PublicKey
import java.util.*
import javax.persistence.NoResultException
import javax.persistence.criteria.CriteriaBuilder

/**
 * A [PublicKeyToOwningIdentityCache] maps public keys to their corresponding owning identity. This may be the node identity, or may be an
 * external UUID (commonly the case when working with accounts).
 *
 * This class caches the result of any database lookup, such that if the same key is accessed multiple times there will not be multiple
 * attempts to access the database.
 */
class PublicKeyToOwningIdentityCache(private val database: CordaPersistence,
                                     cacheSize: Int = 1000) {
    companion object {
        val log = contextLogger()
    }

    private val criteriaBuilder: CriteriaBuilder by lazy { database.hibernateConfig.sessionFactoryForRegisteredSchemas.criteriaBuilder }

    private val cache = createSimpleCache<PublicKey, KeyOwningIdentity>(cacheSize).toSynchronised()

    /**
     * Return the owning identity associated with a given key.
     *
     * This method caches the result of a database lookup to prevent multiple database accesses for the same key. This assumes that once a
     * key is generated, the UUID assigned to it is never changed.
     */
    operator fun get(key: PublicKey): KeyOwningIdentity {
        return cache.computeIfAbsent(key) {
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
}
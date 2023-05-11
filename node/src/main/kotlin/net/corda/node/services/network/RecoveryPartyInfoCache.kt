package net.corda.node.services.network

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.node.services.persistence.DBTransactionRecovery
import net.corda.node.utilities.NonInvalidatingCache
import net.corda.nodeapi.internal.persistence.CordaPersistence
import org.hibernate.Session

class RecoveryPartyInfoCache(private val networkMapCache: PersistentNetworkMapCache,
                             cacheFactory: NamedCacheFactory,
                             private val database: CordaPersistence) {

    private var cordaX500NameToPartyIdCache: NonInvalidatingCache<CordaX500Name, Long>
    private var partyIdToCordaX500NameCache: NonInvalidatingCache<Long, CordaX500Name>

    init {
        cordaX500NameToPartyIdCache = NonInvalidatingCache(
                cacheFactory = cacheFactory,
                name = "RecoveryPartyInfoCache_byCordaX500Name") { key ->
            database.transaction { queryByCordaX500Name(session, key) }
        }
        partyIdToCordaX500NameCache = NonInvalidatingCache(
                cacheFactory = cacheFactory,
                name = "RecoveryPartyInfoCache_byPartyId") { key ->
            database.transaction { queryByPartyId(session, key) }
        }

        val track = networkMapCache.track()
        track.snapshot.map { entry ->
            entry.legalIdentities.map { party ->
                add(party.name.hashCode().toLong(), party.name)
            }
        }
        track.updates.cache().forEach { nodeInfo ->
            nodeInfo.node.legalIdentities.map { party ->
                add(party.name.hashCode().toLong(), party.name)
            }
        }
    }

    fun getPartyIdByCordaX500Name(name: CordaX500Name): Long = cordaX500NameToPartyIdCache[name]!!

    fun getCordaX500NameByPartyId(partyId: Long): CordaX500Name = partyIdToCordaX500NameCache[partyId]!!

    private fun add(partyId: Long, partyName: CordaX500Name) {
        partyIdToCordaX500NameCache.cache.put(partyId, partyName)
        cordaX500NameToPartyIdCache.cache.put(partyName, partyId)
    }

    private fun queryByCordaX500Name(session: Session, key: CordaX500Name): Long {
        val query = session.createQuery(
                "SELECT n FROM ${DBTransactionRecovery.DBRecoveryPartyInfo::class.java.name} WHERE partyName = :partyName",
                DBTransactionRecovery.DBRecoveryPartyInfo::class.java)
        query.setParameter("partyName", key.toString())
        return query.resultList.single().partyId
    }

    private fun queryByPartyId(session: Session, key: Long): CordaX500Name {
        val query = session.createQuery(
                "SELECT n FROM ${DBTransactionRecovery.DBRecoveryPartyInfo::class.java.name} WHERE partyId = :partyId",
                DBTransactionRecovery.DBRecoveryPartyInfo::class.java)
        query.setParameter("partyId", key)
        return query.resultList.single().partyName.let { CordaX500Name.parse(it) }
    }
}
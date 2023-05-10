package net.corda.node.services.network

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.node.services.persistence.DBTransactionRecovery
import net.corda.node.utilities.NonInvalidatingCache
import net.corda.nodeapi.internal.persistence.CordaPersistence
import org.hibernate.Session

class RecoveryPartyInfoCache(cacheFactory: NamedCacheFactory,
                             private val database: CordaPersistence) {

    fun getPartyIdByCordaX500Name(name: CordaX500Name): Long = partyIdByCordaX500NameCache[name]!!

    private val partyIdByCordaX500NameCache = NonInvalidatingCache<CordaX500Name, Long>(
            cacheFactory = cacheFactory,
            name = "RecoveryPartyInfoCache_byCordaX500Name") { key ->
        database.transaction { queryByCordaX500Name(session, key) }
    }

    fun getCordaX500NameByPartyId(partyId: Long): CordaX500Name = cordaX500NameByPartyIdCache[partyId]!!

    private val cordaX500NameByPartyIdCache = NonInvalidatingCache<Long, CordaX500Name>(
            cacheFactory = cacheFactory,
            name = "RecoveryPartyInfoCache_byPartyId") { key ->
        database.transaction { queryByPartyId(session, key) }
    }

    fun add(partyId: Long, partyName: CordaX500Name) {
        cordaX500NameByPartyIdCache.cache.put(partyId, partyName)
        partyIdByCordaX500NameCache.cache.put(partyName, partyId)
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
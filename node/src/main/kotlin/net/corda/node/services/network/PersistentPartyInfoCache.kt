package net.corda.node.services.network

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.node.services.NetworkMapCache
import net.corda.node.services.persistence.DBTransactionStorageLedgerRecovery
import net.corda.node.utilities.NonInvalidatingCache
import net.corda.nodeapi.internal.persistence.CordaPersistence
import org.hibernate.Session
import rx.Observable

class PersistentPartyInfoCache(private val networkMapCache: PersistentNetworkMapCache,
                               cacheFactory: NamedCacheFactory,
                               private val database: CordaPersistence) {

    // probably better off using a BiMap here: https://www.baeldung.com/guava-bimap
    private val cordaX500NameToPartyIdCache = NonInvalidatingCache<CordaX500Name, Long?>(
                cacheFactory = cacheFactory,
                name = "RecoveryPartyInfoCache_byCordaX500Name") { key ->
            database.transaction { queryByCordaX500Name(session, key) }
        }

    private val partyIdToCordaX500NameCache = NonInvalidatingCache<Long, CordaX500Name?>(
                cacheFactory = cacheFactory,
                name = "RecoveryPartyInfoCache_byPartyId") { key ->
            database.transaction { queryByPartyId(session, key) }
        }

    private lateinit var trackNetworkMapUpdates: Observable<NetworkMapCache.MapChange>

    fun start() {
        val (snapshot, updates) = networkMapCache.track()
        snapshot.map { entry ->
            entry.legalIdentities.map { party ->
                add(party.name.hashCode().toLong(), party.name)
            }
        }
        trackNetworkMapUpdates = updates
        trackNetworkMapUpdates.cache().forEach { nodeInfo ->
            nodeInfo.node.legalIdentities.map { party ->
                add(party.name.hashCode().toLong(), party.name)
            }
        }
    }

    fun getPartyIdByCordaX500Name(name: CordaX500Name): Long = cordaX500NameToPartyIdCache[name] ?: throw IllegalStateException("Missing cache entry for $name")

    fun getCordaX500NameByPartyId(partyId: Long): CordaX500Name = partyIdToCordaX500NameCache[partyId] ?: throw IllegalStateException("Missing cache entry for $partyId")

    private fun add(partyHashCode: Long, partyName: CordaX500Name) {
        partyIdToCordaX500NameCache.cache.put(partyHashCode, partyName)
        cordaX500NameToPartyIdCache.cache.put(partyName, partyHashCode)
        updateInfoDB(partyHashCode, partyName)
    }

    private fun updateInfoDB(partyHashCode: Long, partyName: CordaX500Name) {
        database.transaction {
            if (queryByPartyId(session, partyHashCode) == null) {
                println("PartyInfo: $partyHashCode -> $partyName")
                session.save(DBTransactionStorageLedgerRecovery.DBRecoveryPartyInfo(partyHashCode, partyName.toString()))
            }
        }
    }

    private fun queryByCordaX500Name(session: Session, key: CordaX500Name): Long? {
        val query = session.createQuery(
                "FROM ${DBTransactionStorageLedgerRecovery.DBRecoveryPartyInfo::class.java.name} WHERE partyName = :partyName",
                DBTransactionStorageLedgerRecovery.DBRecoveryPartyInfo::class.java)
        query.setParameter("partyName", key.toString())
        return query.resultList.singleOrNull()?.partyId
    }

    private fun queryByPartyId(session: Session, key: Long): CordaX500Name? {
        val query = session.createQuery(
                "FROM ${DBTransactionStorageLedgerRecovery.DBRecoveryPartyInfo::class.java.name} WHERE partyId = :partyId",
                DBTransactionStorageLedgerRecovery.DBRecoveryPartyInfo::class.java)
        query.setParameter("partyId", key)
        return query.resultList.singleOrNull()?.partyName?.let { CordaX500Name.parse(it) }
    }
}
package net.corda.node.services.network

import net.corda.core.identity.AbstractParty
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.node.services.api.NetworkMapCacheBaseInternal
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.nodeapi.internal.persistence.CordaPersistence

class NetworkMapCacheImpl(
        private val networkMapCacheBase: NetworkMapCacheBaseInternal,
        private val identityService: IdentityService,
        private val database: CordaPersistence
) : NetworkMapCacheBaseInternal by networkMapCacheBase, NetworkMapCacheInternal, SingletonSerializeAsToken() {
    companion object {
        private val logger = contextLogger()
    }

    fun start() {
        for (nodeInfo in networkMapCacheBase.allNodes) {
            for (identity in nodeInfo.legalIdentitiesAndCerts) {
                identityService.verifyAndRegisterIdentity(identity)
            }
        }
        networkMapCacheBase.changed.subscribe { mapChange ->
            // TODO how should we handle network map removal
            if (mapChange is NetworkMapCache.MapChange.Added) {
                mapChange.node.legalIdentitiesAndCerts.forEach {
                    try {
                        identityService.verifyAndRegisterIdentity(it)
                    } catch (ignore: Exception) {
                        // Log a warning to indicate node info is not added to the network map cache.
                        logger.warn("Node info for :'${it.name}' is not added to the network map due to verification error.")
                    }
                }
            }
        }
    }

    override fun getNodeByLegalIdentity(party: AbstractParty): NodeInfo? {
        return database.transaction {
            val wellKnownParty = identityService.wellKnownPartyFromAnonymous(party)
            wellKnownParty?.let {
                getNodesByLegalIdentityKey(it.owningKey).firstOrNull()
            }
        }
    }
}

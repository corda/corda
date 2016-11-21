package net.corda.node.services.identity

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 */
@ThreadSafe
class InMemoryIdentityService() : SingletonSerializeAsToken(), IdentityService {
    private val keyToParties = ConcurrentHashMap<CompositeKey, Party>()
    private val nameToParties = ConcurrentHashMap<String, Party>()

    override fun registerIdentity(party: Party) {
        keyToParties[party.owningKey] = party
        nameToParties[party.name] = party
    }

    override fun partyFromKey(key: CompositeKey): Party? = keyToParties[key]
    override fun partyFromName(name: String): Party? = nameToParties[name]
}

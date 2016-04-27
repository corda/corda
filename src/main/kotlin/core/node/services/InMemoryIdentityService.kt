package core.node.services

import core.Party
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 */
@ThreadSafe
class InMemoryIdentityService() : IdentityService {
    private val keyToParties = ConcurrentHashMap<PublicKey, Party>()
    private val nameToParties = ConcurrentHashMap<String, Party>()

    override fun registerIdentity(party: Party) {
        keyToParties[party.owningKey] = party
        nameToParties[party.name] = party
    }

    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]
    override fun partyFromName(name: String): Party? = nameToParties[name]
}
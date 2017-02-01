package net.corda.node.services.identity

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.concurrent.ThreadSafe

/**
 * Simple identity service which caches parties and provides functionality for efficient lookup.
 */
@ThreadSafe
class InMemoryIdentityService() : SingletonSerializeAsToken(), IdentityService {
    private val keyToParties = ConcurrentHashMap<CompositeKey, Party.Full>()
    private val nameToParties = ConcurrentHashMap<String, Party.Full>()

    override fun registerIdentity(party: Party.Full) {
        keyToParties[party.owningKey] = party
        nameToParties[party.name] = party
    }

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<Party.Full> = ArrayList(keyToParties.values)

    override fun partyFromKey(key: CompositeKey): Party.Full? = keyToParties[key]
    override fun partyFromName(name: String): Party.Full? = nameToParties[name]
}

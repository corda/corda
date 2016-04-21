package core.testing

import core.Party
import core.node.services.IdentityService
import java.security.PublicKey
import javax.annotation.concurrent.ThreadSafe

/**
 * Scaffolding: a dummy identity service that just expects to have identities loaded off disk or found elsewhere.
 * This class allows the provided list of identities to be mutated after construction, so it takes the list lock
 * when doing lookups and recalculates the mapping each time. The ability to change the list is used by the
 * MockNetwork code.
 */
@ThreadSafe
class MockIdentityService(val identities: List<Party>) : IdentityService {
    private val keyToParties: Map<PublicKey, Party>
        get() = synchronized(identities) { identities.associateBy { it.owningKey } }
    private val nameToParties: Map<String, Party>
        get() = synchronized(identities) { identities.associateBy { it.name } }

    override fun registerIdentity(party: Party) { throw UnsupportedOperationException() }
    override fun deregisterIdentity(party: Party) { throw UnsupportedOperationException() }
    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]
    override fun partyFromName(name: String): Party? = nameToParties[name]
}
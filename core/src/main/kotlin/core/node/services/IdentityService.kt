package core.node.services

import core.Party
import java.security.PublicKey

/**
 * An identity service maintains an bidirectional map of [Party]s to their associated public keys and thus supports
 * lookup of a party given its key. This is obviously very incomplete and does not reflect everything a real identity
 * service would provide.
 */
interface IdentityService {
    object Type : ServiceType("corda.identity")
    fun partyFromKey(key: PublicKey): Party?
    fun partyFromName(name: String): Party?
}

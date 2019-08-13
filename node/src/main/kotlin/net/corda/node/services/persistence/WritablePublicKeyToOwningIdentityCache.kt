package net.corda.node.services.persistence

import net.corda.nodeapi.internal.KeyOwningIdentity
import net.corda.nodeapi.internal.PublicKeyToOwningIdentityCache
import java.security.PublicKey

/**
 * Internal only version of a [PublicKeyToOwningIdentityCache] that allows writing to the cache and underlying database table
 */
interface WritablePublicKeyToOwningIdentityCache : PublicKeyToOwningIdentityCache {

    /**
     * Assign a public key to an owning identity.
     */
    operator fun set(key: PublicKey, value: KeyOwningIdentity)
}
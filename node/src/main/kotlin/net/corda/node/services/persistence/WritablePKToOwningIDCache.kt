package net.corda.node.services.persistence

import net.corda.nodeapi.internal.persistence.KeyOwningIdentity
import net.corda.nodeapi.internal.persistence.PublicKeyToOwningIdentityCache
import java.security.PublicKey

/**
 * Internal only version of a [PublicKeyToOwningIdentityCache] that allows writing to the cache and underlying database table
 */
interface WritablePKToOwningIDCache : PublicKeyToOwningIdentityCache {

    /**
     * Assign a public key to an owning identity.
     */
    operator fun set(key: PublicKey, value: KeyOwningIdentity)
}
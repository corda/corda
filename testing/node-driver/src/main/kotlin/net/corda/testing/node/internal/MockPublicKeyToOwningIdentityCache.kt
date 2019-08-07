package net.corda.testing.node.internal

import net.corda.core.internal.toSynchronised
import net.corda.nodeapi.internal.persistence.KeyOwningIdentity
import net.corda.nodeapi.internal.persistence.PublicKeyToOwningIdentityCache
import java.security.PublicKey

/**
 * A mock implementation of [PublicKeyToOwningIdentityCache] that stores all key mappings in memory. Used in testing scenarios that do not
 * require database access.
 */
class MockPublicKeyToOwningIdentityCache : PublicKeyToOwningIdentityCache {

    private val cache: MutableMap<PublicKey, KeyOwningIdentity> = mutableMapOf<PublicKey, KeyOwningIdentity>().toSynchronised()

    override fun get(key: PublicKey): KeyOwningIdentity {
        return cache.getOrPut(key) { KeyOwningIdentity.NodeIdentity }
    }

    override fun set(key: PublicKey, value: KeyOwningIdentity) {
        cache[key] = value
    }
}
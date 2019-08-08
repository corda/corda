package net.corda.testing.node.internal

import net.corda.core.internal.toSynchronised
import net.corda.node.services.persistence.WritablePKToOwningIDCache
import net.corda.nodeapi.internal.persistence.KeyOwningIdentity
import java.security.PublicKey

/**
 * A mock implementation of [WritablePKToOwningIDCache] that stores all key mappings in memory. Used in testing scenarios that do not
 * require database access.
 */
class MockPublicKeyToOwningIdentityCache : WritablePKToOwningIDCache {

    private val cache: MutableMap<PublicKey, KeyOwningIdentity> = mutableMapOf<PublicKey, KeyOwningIdentity>().toSynchronised()

    override fun get(key: PublicKey): KeyOwningIdentity {
        return cache.getOrPut(key) { KeyOwningIdentity.NodeIdentity }
    }

    override fun set(key: PublicKey, value: KeyOwningIdentity) {
        cache[key] = value
    }
}
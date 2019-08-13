package net.corda.testing.node.internal

import net.corda.core.internal.toSynchronised
import net.corda.node.services.persistence.WritablePublicKeyToOwningIdentityCache
import net.corda.nodeapi.internal.KeyOwningIdentity
import java.security.PublicKey

/**
 * A mock implementation of [WritablePublicKeyToOwningIdentityCache] that stores all key mappings in memory. Used in testing scenarios that do not
 * require database access.
 */
class MockPublicKeyToOwningIdentityCache : WritablePublicKeyToOwningIdentityCache {

    private val cache: MutableMap<PublicKey, KeyOwningIdentity> = mutableMapOf<PublicKey, KeyOwningIdentity>().toSynchronised()

    override fun get(key: PublicKey): KeyOwningIdentity? {
        return cache[key]
    }

    override fun set(key: PublicKey, value: KeyOwningIdentity) {
        cache[key] = value
    }
}
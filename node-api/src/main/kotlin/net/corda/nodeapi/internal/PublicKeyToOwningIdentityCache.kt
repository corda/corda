package net.corda.nodeapi.internal

import java.security.PublicKey

/**
 * A [PublicKeyToOwningIdentityCache] maps public keys to their owners. In this case, an owner could be the node identity, or it could be
 * an external identity.
 */
interface PublicKeyToOwningIdentityCache {

    /**
     * Obtain the owning identity for a public key.
     *
     * Typically, implementations of this are backed by the database, and so attempting to get a key that is not present in memory will
     * result in database accesses to establish the owning identity of the key.
     */
    operator fun get(key: PublicKey): KeyOwningIdentity
}
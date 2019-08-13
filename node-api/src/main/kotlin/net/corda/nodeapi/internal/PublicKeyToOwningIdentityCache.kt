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
     * If the key is unknown to the node, then this will return null.
     */
    operator fun get(key: PublicKey): KeyOwningIdentity?
}
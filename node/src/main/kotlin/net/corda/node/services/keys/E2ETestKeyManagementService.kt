package net.corda.node.services.keys

import net.corda.core.ThreadBox
import net.corda.core.crypto.generateKeyPair
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * A simple in-memory KMS that doesn't bother saving keys to disk. A real implementation would:
 *
 * - Probably be accessed via the network layer as an internal node service i.e. via a message queue, so it can run
 *   on a separate/firewalled service.
 * - Use the flow framework so requests to fetch keys can be suspended whilst a human signs off on the request.
 * - Use deterministic key derivation.
 * - Possibly have some sort of TREZOR-like two-factor authentication ability.
 *
 * etc.
 */
@ThreadSafe
class E2ETestKeyManagementService(initialKeys: Set<KeyPair>) : SingletonSerializeAsToken(), KeyManagementService {
    private class InnerState {
        val keys = HashMap<PublicKey, PrivateKey>()
    }

    private val mutex = ThreadBox(InnerState())

    init {
        mutex.locked {
            for (key in initialKeys) {
                keys[key.public] = key.private
            }
        }
    }

    // Accessing this map clones it.
    override val keys: Map<PublicKey, PrivateKey> get() = mutex.locked { HashMap(keys) }

    override fun freshKey(): KeyPair {
        val keyPair = generateKeyPair()
        mutex.locked {
            keys[keyPair.public] = keyPair.private
        }
        return keyPair
    }
}

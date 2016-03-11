/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.services

import core.node.services.KeyManagementService
import core.ThreadBox
import core.crypto.generateKeyPair
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
 * - Use the protocol framework so requests to fetch keys can be suspended whilst a human signs off on the request.
 * - Use deterministic key derivation.
 * - Possibly have some sort of TREZOR-like two-factor authentication ability
 *
 * etc
 */
@ThreadSafe
class E2ETestKeyManagementService : KeyManagementService {
    private class InnerState {
        val keys = HashMap<PublicKey, PrivateKey>()
    }
    private val mutex = ThreadBox(InnerState())

    // Accessing this map clones it.
    override val keys: Map<PublicKey, PrivateKey> get() = mutex.locked { HashMap(keys) }

    override fun freshKey(): KeyPair {
        val keypair = generateKeyPair()
        mutex.locked {
            keys[keypair.public] = keypair.private
        }
        return keypair
    }
}
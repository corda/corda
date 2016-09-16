package com.r3corda.node.services.keys

import com.r3corda.core.ThreadBox
import com.r3corda.core.crypto.generateKeyPair
import com.r3corda.core.node.services.KeyManagementService
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.node.utilities.JDBCHashMap
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * A persistent re-implementation of [E2ETestKeyManagementService] to support node re-start.
 *
 * This is not the long-term implementation.  See the list of items in the above class.
 *
 * This class needs database transactions to be in-flight during method calls and init.
 */
class PersistentKeyManagementService(initialKeys: Set<KeyPair>) : SingletonSerializeAsToken(), KeyManagementService {
    private class InnerState {
        val keys = JDBCHashMap<PublicKey, PrivateKey>("key_pairs", loadOnInit = false)
    }

    private val mutex = ThreadBox(InnerState())

    init {
        mutex.locked {
            keys.putAll(initialKeys.associate { Pair(it.public, it.private) })
        }
    }

    override val keys: Map<PublicKey, PrivateKey> get() = mutex.locked { HashMap(keys) }

    override fun freshKey(): KeyPair {
        val keypair = generateKeyPair()
        mutex.locked {
            keys[keypair.public] = keypair.private
        }
        return keypair
    }
}

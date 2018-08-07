package net.corda.node.services.keys

import net.corda.core.crypto.*
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.ThreadBox
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.SingletonSerializeAsToken
import org.bouncycastle.operator.ContentSigner
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
class E2ETestKeyManagementService(val identityService: IdentityService) : SingletonSerializeAsToken(), KeyManagementServiceInternal {
    private class InnerState {
        val keys = HashMap<PublicKey, PrivateKey>()
    }

    private val mutex = ThreadBox(InnerState())
    // Accessing this map clones it.
    override val keys: Set<PublicKey> get() = mutex.locked { keys.keys }
    val keyPairs: Set<KeyPair> get() = mutex.locked { keys.map { KeyPair(it.key, it.value) }.toSet() }

    override fun start(initialKeyPairs: Set<KeyPair>) {
        mutex.locked {
            for (key in initialKeyPairs) {
                keys[key.public] = key.private
            }
        }
    }

    override fun freshKey(): PublicKey {
        val keyPair = generateKeyPair()
        mutex.locked {
            keys[keyPair.public] = keyPair.private
        }
        return keyPair.public
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey))
    }

    private fun getSigner(publicKey: PublicKey): ContentSigner = getSigner(getSigningKeyPair(publicKey))

    private fun getSigningKeyPair(publicKey: PublicKey): KeyPair {
        return mutex.locked {
            val pk = publicKey.keys.first { keys.containsKey(it) }
            KeyPair(pk, keys[pk]!!)
        }
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        return mutex.locked { candidateKeys.filter { it in this.keys } }
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        val keyPair = getSigningKeyPair(publicKey)
        return keyPair.sign(bytes)
    }

    // TODO: A full KeyManagementService implementation needs to record activity to the Audit Service and to limit
    //      signing to appropriately authorised contexts and initiating users.
    override fun sign(signableData: SignableData, publicKey: PublicKey): TransactionSignature {
        val keyPair = getSigningKeyPair(publicKey)
        return keyPair.sign(signableData)
    }
}

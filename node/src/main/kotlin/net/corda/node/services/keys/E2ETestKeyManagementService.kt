package net.corda.node.services.keys

import net.corda.core.ThreadBox
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.keys
import net.corda.core.crypto.sign
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertPath
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
class E2ETestKeyManagementService(val identityService: IdentityService,
                                  initialKeys: Set<KeyPair>) : SingletonSerializeAsToken(), KeyManagementService {
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
    override val keys: Set<PublicKey> get() = mutex.locked { keys.keys }

    override fun freshKey(): PublicKey {
        val keyPair = generateKeyPair()
        mutex.locked {
            keys[keyPair.public] = keyPair.private
        }
        return keyPair.public
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): Pair<X509CertificateHolder, CertPath> {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey), revocationEnabled)
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
        val signature = keyPair.sign(bytes)
        return signature
    }
}

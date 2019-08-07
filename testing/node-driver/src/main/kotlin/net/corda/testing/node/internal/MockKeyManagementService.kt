package net.corda.testing.node.internal

import net.corda.core.crypto.*
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.keys.freshCertificate
import net.corda.nodeapi.internal.persistence.KeyOwningIdentity
import net.corda.nodeapi.internal.persistence.PublicKeyToOwningIdentityCache
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * A class which provides an implementation of [KeyManagementService] which is used in [MockServices]
 *
 * @property identityService The [IdentityService] which contains the given identities.
 */
class MockKeyManagementService(val identityService: IdentityService,
                               vararg initialKeys: KeyPair,
                               private val pkToIdCache: PublicKeyToOwningIdentityCache) : SingletonSerializeAsToken(), KeyManagementService {
    private val keyStore: MutableMap<PublicKey, PrivateKey> = initialKeys.associateByTo(HashMap(), { it.public }, { it.private })

    override val keys: Set<PublicKey> get() = keyStore.keys

    private val nextKeys = LinkedList<KeyPair>()

    private fun generateKey(): PublicKey {
        val k = nextKeys.poll() ?: generateKeyPair()
        keyStore[k.public] = k.private
        return k.public
    }

    override fun freshKey(): PublicKey {
        val k = generateKey()
        pkToIdCache[k] = KeyOwningIdentity.fromUUID(null)
        return k
    }

    override fun freshKey(externalId: UUID): PublicKey {
        val k = generateKey()
        pkToIdCache[k] = KeyOwningIdentity.fromUUID(externalId)
        return k
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean, externalId: UUID): PartyAndCertificate {
        val keyAndCert = freshCertificate(identityService, generateKey(), identity, getSigner(identity.owningKey))
        pkToIdCache[keyAndCert.owningKey] = KeyOwningIdentity.fromUUID(externalId)
        return keyAndCert
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> = candidateKeys.filter { it in this.keys }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate {
        val keyAndCert = freshCertificate(identityService, generateKey(), identity, getSigner(identity.owningKey))
        pkToIdCache[keyAndCert.owningKey] = KeyOwningIdentity.fromUUID(null)
        return keyAndCert
    }

    private fun getSigner(publicKey: PublicKey): ContentSigner = net.corda.node.services.keys.getSigner(getSigningKeyPair(publicKey))

    private fun getSigningKeyPair(publicKey: PublicKey): KeyPair {
        val pk = publicKey.keys.firstOrNull { keyStore.containsKey(it) }
                ?: throw IllegalArgumentException("Public key not found: ${publicKey.toStringShort()}")
        return KeyPair(pk, keyStore[pk]!!)
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        val keyPair = getSigningKeyPair(publicKey)
        return keyPair.sign(bytes)
    }

    override fun sign(signableData: SignableData, publicKey: PublicKey): TransactionSignature {
        val keyPair = getSigningKeyPair(publicKey)
        return keyPair.sign(signableData)
    }
}
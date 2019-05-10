package net.corda.testing.node.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.*
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.keys.freshCertificate
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A class which provides an implementation of [KeyManagementService] which is used in [MockServices]
 *
 * @property identityService The [IdentityService] which contains the given identities.
 */
class MockKeyManagementService(val identityService: IdentityService,
                               vararg initialKeys: KeyPair) : SingletonSerializeAsToken(), KeyManagementService {


    private val keyStore: MutableMap<PublicKey, PrivateKey> = initialKeys.associateByTo(HashMap(), { it.public }, { it.private })

    override val keys: Set<PublicKey> get() = keyStore.keys

    private val nextKeys = LinkedList<KeyPair>()

    val keysById: MutableMap<UUID, Set<PublicKey>> = ConcurrentHashMap()

    override fun freshKey(): PublicKey {
        val k = nextKeys.poll() ?: generateKeyPair()
        keyStore[k.public] = k.private
        return k.public
    }

    private fun mapKeyToId(publicKey: PublicKey, externalId: UUID) {
        val keysForId = keysById.getOrPut(externalId) { emptySet() }
        keysById[externalId] = keysForId + publicKey
    }

    override fun freshKey(externalId: UUID): PublicKey {
        val key = freshKey()
        mapKeyToId(key, externalId)
        return key
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean, externalId: UUID): PartyAndCertificate {
        val keyAndCert = freshKeyAndCert(identity, revocationEnabled)
        mapKeyToId(keyAndCert.owningKey, externalId)
        return keyAndCert
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> = candidateKeys.filter { it in this.keys }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey))
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

    @Suspendable
    override fun externalIdForPublicKey(publicKey: PublicKey): UUID? {
        return keysById.filter { publicKey in it.value }.map { it.key }.singleOrNull()
    }


}
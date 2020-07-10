package net.corda.testing.node.internal

import net.corda.core.crypto.*
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.keys.KeyManagementServiceInternal
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
class MockKeyManagementService(
        override val identityService: IdentityService,
        vararg initialKeys: KeyPair
) : SingletonSerializeAsToken(), KeyManagementServiceInternal {

    private val keyStore: MutableMap<PublicKey, PrivateKey> = initialKeys.associateByTo(HashMap(), { it.public }, { it.private })

    override val keys: Set<PublicKey> get() = keyStore.keys

    private val nextKeys = LinkedList<KeyPair>()

    override fun freshKeyInternal(externalId: UUID?): PublicKey {
        val k = nextKeys.poll() ?: generateKeyPair()
        keyStore[k.public] = k.private
        if (externalId != null) {
            (identityService as InMemoryIdentityService).registerKeyToExternalId(k.public, externalId)
        }
        return k.public
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> = candidateKeys.filter { it in this.keys }

    override fun getSigner(publicKey: PublicKey): ContentSigner = net.corda.node.services.keys.getSigner(getSigningKeyPair(publicKey))

    override fun start(initialKeysAndAliases: List<Pair<PublicKey, String>>) {
        throw NotImplementedError()
    }

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
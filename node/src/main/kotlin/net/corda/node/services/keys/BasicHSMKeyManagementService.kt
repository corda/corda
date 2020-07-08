package net.corda.node.services.keys

import net.corda.core.crypto.*
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.cryptoservice.SignOnlyCryptoService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import javax.persistence.*
import kotlin.collections.LinkedHashSet

/**
 * A persistent re-implementation of [KeyManagementServiceInternal] to support CryptoService for initial keys and
 * database storage for anonymous fresh keys.
 *
 * This is not the long-term implementation.  See the list of items in the above class.
 *
 * This class needs database transactions to be in-flight during method calls and init.
 */
class BasicHSMKeyManagementService(
        cacheFactory: NamedCacheFactory,
        override val identityService: PersistentIdentityService,
        private val database: CordaPersistence,
        private val cryptoService: SignOnlyCryptoService
) : SingletonSerializeAsToken(), KeyManagementServiceInternal {

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}our_key_pairs")
    class PersistentKey(
            @Id
            @Column(name = "public_key_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String,

            @Lob
            @Column(name = "public_key", nullable = false)
            var publicKey: ByteArray = EMPTY_BYTE_ARRAY,
            @Lob
            @Column(name = "private_key", nullable = false)
            var privateKey: ByteArray = EMPTY_BYTE_ARRAY
    ) {
        constructor(publicKey: PublicKey, privateKey: PrivateKey)
            : this(publicKey.toStringShort(), publicKey.encoded, privateKey.encoded)
    }

    private companion object {
        fun createKeyMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<PublicKey, PrivateKey, PersistentKey, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "BasicHSMKeyManagementService_keys",
                    toPersistentEntityKey = { it.toStringShort() },
                    fromPersistentEntity = { Pair(Crypto.decodePublicKey(it.publicKey), Crypto.decodePrivateKey(
                            it.privateKey)) },
                    toPersistentEntity = { key: PublicKey, value: PrivateKey ->
                        PersistentKey(key, value)
                    },
                    persistentEntityClass = PersistentKey::class.java
            )
        }
    }

    // Maintain a map from PublicKey to alias for the initial keys.
    private val originalKeysMap = mutableMapOf<PublicKey, String>()
    // A map for anonymous keys.
    private val keysMap = createKeyMap(cacheFactory)

    override fun start(initialKeyAliasPairs: Set<Pair<PublicKey, String>>) {
        initialKeyAliasPairs.forEach {
            originalKeysMap[Crypto.toSupportedPublicKey(it.first)] = it.second
        }
    }

    override val keys: Set<PublicKey>
        get() {
            return database.transaction {
                val set = LinkedHashSet<PublicKey>(originalKeysMap.keys)
                keysMap.allPersisted.use { it.forEach { set += it.first } }
                set
            }
        }

    private fun containsPublicKey(publicKey: PublicKey): Boolean {
        return (publicKey in originalKeysMap || publicKey in keysMap)
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> = database.transaction {
        candidateKeys.filter(::containsPublicKey)
    }

    override fun freshKeyInternal(externalId: UUID?): PublicKey {
        val keyPair = generateKeyPair()
        database.transaction {
            keysMap[keyPair.public] = keyPair.private
            // Register the key to our identity.
            // No checks performed here as entries for the new key couldn't have existed before in the maps.
            identityService.registerKeyToParty(keyPair.public)
            if (externalId != null) {
                identityService.registerKeyToExternalId(keyPair.public, externalId)
            }
        }
        return keyPair.public
    }

    override fun getSigner(publicKey: PublicKey): ContentSigner {
        val signingPublicKey = getSigningPublicKey(publicKey)
        return if (signingPublicKey in originalKeysMap) {
            cryptoService.getSigner(originalKeysMap[signingPublicKey]!!)
        } else {
            getSigner(getSigningKeyPair(signingPublicKey))
        }
    }

    // Get [KeyPair] for the input [publicKey]. This is used for fresh keys, in which we have access to the private key material.
    private fun getSigningKeyPair(publicKey: PublicKey): KeyPair {
        return database.transaction {
            KeyPair(publicKey, keysMap[publicKey]!!)
        }
    }

    // It looks for the PublicKey in the (potentially) CompositeKey that is ours.
    // TODO what if we own two or more leaves of a CompositeKey?
    private fun getSigningPublicKey(publicKey: PublicKey): PublicKey {
        return publicKey.keys.first { containsPublicKey(it) }
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        val signingPublicKey = getSigningPublicKey(publicKey)
        return if (signingPublicKey in originalKeysMap) {
            DigitalSignature.WithKey(signingPublicKey, cryptoService.sign(originalKeysMap[signingPublicKey]!!, bytes))
        } else {
            val keyPair = getSigningKeyPair(signingPublicKey)
            keyPair.sign(bytes)
        }
    }

    // TODO: A full KeyManagementService implementation needs to record activity to the Audit Service and to limit
    //      signing to appropriately authorised contexts and initiating users.
    override fun sign(signableData: SignableData, publicKey: PublicKey): TransactionSignature {
        val signingPublicKey = getSigningPublicKey(publicKey)
        return if (signingPublicKey in originalKeysMap) {
            val sigKey: SignatureScheme = Crypto.findSignatureScheme(signingPublicKey)
            val sigMetaData: SignatureScheme = Crypto.findSignatureScheme(signableData.signatureMetadata.schemeNumberID)
            require(sigKey == sigMetaData || sigMetaData == Crypto.COMPOSITE_KEY) {
                "Metadata schemeCodeName: ${sigMetaData.schemeCodeName} is not aligned with the key type: ${sigKey.schemeCodeName}."
            }
            val signatureBytes = cryptoService.sign(originalKeysMap[signingPublicKey]!!, signableData.serialize().bytes)
            TransactionSignature(signatureBytes, signingPublicKey, signableData.signatureMetadata)
        } else {
            val keyPair = getSigningKeyPair(signingPublicKey)
            keyPair.sign(signableData)
        }
    }
}

package net.corda.node.services.keys

import net.corda.core.crypto.*
import net.corda.core.crypto.internal.AliasPrivateKey
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService.PrivateKeyType.*
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.WrappedPrivateKey
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import javax.persistence.*

/**
 * A persistent re-implementation of [E2ETestKeyManagementService] to support CryptoService for initial keys and
 * database storage for anonymous fresh keys.
 *
 * This is not the long-term implementation.  See the list of items in the above class.
 *
 * This class needs database transactions to be in-flight during method calls and init.
 *
 * Notes:
 * - For signing operations with the keys imported during startup, [cryptoService] is used.
 * - For generating fresh (anonymous) keys:
 *  - if a [wrappingCryptoService] is configured, then this is used and the generated keys are wrapped and stored encrypted at rest.
 *  - if no [wrappingCryptoService] is configured, then no wrapping is used, keys are generated in software and stored unencrypted at rest.
 * - Signing with fresh keys that have been previously generated will be performed locally or using [wrappingCryptoService], depending
 *  on how the keys were generated and stored.
 */
class BasicHSMKeyManagementService(cacheFactory: NamedCacheFactory, val identityService: PersistentIdentityService,
                                   private val database: CordaPersistence, private val cryptoService: CryptoService) : SingletonSerializeAsToken(), KeyManagementServiceInternal {
    private var wrappingCryptoService: CryptoService? = null
    private var wrappingKeyAlias: String? = null

    constructor(cacheFactory: NamedCacheFactory, identityService: PersistentIdentityService,
                database: CordaPersistence, cryptoService: CryptoService,
                wrappingCryptoService: CryptoService, wrappingKeyAlias: String): this(cacheFactory, identityService, database, cryptoService) {
        this.wrappingCryptoService = wrappingCryptoService
        this.wrappingKeyAlias = wrappingKeyAlias
    }

    @VisibleForTesting
    public fun wrappingEnabled() = wrappingCryptoService != null

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
            @Column(name = "private_key", nullable = true)
            var privateKey: ByteArray? = EMPTY_BYTE_ARRAY,
            @Lob
            @Column(name = "private_key_material_wrapped", nullable = true)
            var privateWrappedKey: ByteArray?,
            @Column(name = "scheme_code_name", nullable = true)
            var schemeCodeName: String?
    ) {
        constructor(publicKey: PublicKey, privateKey: PrivateKey)
            : this(publicKey.toStringShort(), publicKey.encoded, privateKey.encoded, null, null)

        constructor(publicKey: PublicKey, wrappedPrivateKey: WrappedPrivateKey)
            : this(publicKey.toStringShort(), publicKey.encoded, null, wrappedPrivateKey.keyMaterial, wrappedPrivateKey.signatureScheme.schemeCodeName)
    }

    private class GenericPrivateKey {
        val privateKey: PrivateKey?
        val wrappedPrivateKey: WrappedPrivateKey?

        constructor(privateKey: PrivateKey) {
            this.privateKey = privateKey
            this.wrappedPrivateKey = null
        }

        constructor(wrappedPrivateKey: WrappedPrivateKey) {
            this.privateKey = null
            this.wrappedPrivateKey = wrappedPrivateKey
        }

        fun getType(): PrivateKeyType {
            return if (privateKey != null) {
                REGULAR
            } else {
                WRAPPED
            }
        }

        companion object {
            fun fromPersistentKey(persistentKey: PersistentKey): GenericPrivateKey {
                return if (persistentKey.privateKey == null) {
                    GenericPrivateKey(WrappedPrivateKey(persistentKey.privateWrappedKey!!, Crypto.findSignatureScheme(persistentKey.schemeCodeName!!)))
                } else {
                    GenericPrivateKey(Crypto.decodePrivateKey(persistentKey.privateKey!!))
                }
            }
            fun toPersistentKey(publicKey: PublicKey, genericPrivateKey: GenericPrivateKey): PersistentKey {
                return if (genericPrivateKey.privateKey == null) {
                    PersistentKey(publicKey, genericPrivateKey.wrappedPrivateKey!!)
                } else {
                    PersistentKey(publicKey, genericPrivateKey.privateKey)
                }
            }
        }
    }

    private enum class PrivateKeyType {
        REGULAR,
        WRAPPED
    }

    private companion object {
        fun createKeyMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<PublicKey, GenericPrivateKey, PersistentKey, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "BasicHSMKeyManagementService_keys",
                    toPersistentEntityKey = { it.toStringShort() },
                    fromPersistentEntity = { Pair(Crypto.decodePublicKey(it.publicKey), GenericPrivateKey.fromPersistentKey(it)) },
                    toPersistentEntity = { key: PublicKey, value: GenericPrivateKey ->
                        GenericPrivateKey.toPersistentKey(key, value)
                    },
                    persistentEntityClass = PersistentKey::class.java
            )
        }
    }

    // Maintain a map from PublicKey to alias for the initial keys.
    private val originalKeysMap = mutableMapOf<PublicKey, String>()
    // A map for anonymous keys.
    private val keysMap = createKeyMap(cacheFactory)

    override fun start(initialKeyPairs: Set<KeyPair>) {
        initialKeyPairs.forEach {
            require(it.private is AliasPrivateKey) { "${this.javaClass.name} supports AliasPrivateKeys only, but ${it.private.algorithm} key was found" }
            originalKeysMap[Crypto.toSupportedPublicKey(it.public)] = (it.private as AliasPrivateKey).alias
        }
    }

    override val keys: Set<PublicKey> get() = database.transaction {
        originalKeysMap.keys
                .plus(keysMap.allPersisted().map { it.first }.toSet())
    }

    private fun containsPublicKey(publicKey: PublicKey): Boolean {
        return publicKey in originalKeysMap || publicKey in keysMap
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> = database.transaction {
        identityService.stripNotOurKeys(candidateKeys)
    }

    override fun freshKey(): PublicKey {
        if (wrappingCryptoService == null) {
            val keyPair = generateKeyPair()
            database.transaction {
                keysMap[keyPair.public] = GenericPrivateKey(keyPair.private)
            }
            return keyPair.public
        } else {
            val (publicKey, privateWrappedKey) = wrappingCryptoService!!.generateWrappedKeyPair(wrappingKeyAlias!!)
            database.transaction {
                keysMap[publicKey] = GenericPrivateKey(privateWrappedKey)
            }
            return publicKey
        }


    }

    override fun freshKey(externalId: UUID): PublicKey {
        val newKey = freshKey()
        database.transaction { session.persist(PublicKeyHashToExternalId(externalId, newKey)) }
        return newKey
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey))
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean, externalId: UUID): PartyAndCertificate {
        val newKeyWithCert = freshKeyAndCert(identity, revocationEnabled)
        database.transaction { session.persist(PublicKeyHashToExternalId(externalId, newKeyWithCert.owningKey)) }
        return newKeyWithCert
    }

    private fun getSigner(publicKey: PublicKey): ContentSigner {
        val signingPublicKey = getSigningPublicKey(publicKey)
        return cryptoService.getSigner(originalKeysMap[signingPublicKey]!!)
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
            val genericPrivateKey = database.transaction {
                keysMap[signingPublicKey]!!
            }
            when (genericPrivateKey.getType()) {
                REGULAR -> {
                    val keyPair = KeyPair(signingPublicKey, genericPrivateKey.privateKey)
                    keyPair.sign(bytes)
                }
                WRAPPED -> {
                    DigitalSignature.WithKey(signingPublicKey, wrappingCryptoService!!.sign(wrappingKeyAlias!!, genericPrivateKey.wrappedPrivateKey!!, bytes))
                }
            }
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
            val genericPrivateKey = database.transaction {
                keysMap[signingPublicKey]!!
            }
            when (genericPrivateKey.getType()) {
                REGULAR -> {
                    val keyPair = KeyPair(signingPublicKey, genericPrivateKey.privateKey)
                    keyPair.sign(signableData)
                }
                WRAPPED -> {
                    val signatureBytes = wrappingCryptoService!!.sign(wrappingKeyAlias!!, genericPrivateKey.wrappedPrivateKey!!, signableData.serialize().bytes)
                    TransactionSignature(signatureBytes, signingPublicKey, signableData.signatureMetadata)
                }
            }
        }
    }
}

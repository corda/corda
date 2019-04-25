package net.corda.node.services.keys

import net.corda.core.crypto.*
import net.corda.core.crypto.internal.AliasPrivateKey
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.node.utilities.AppendOnlyPersistentMapBase
import net.corda.nodeapi.internal.cryptoservice.CryptoService
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
 */
class BasicHSMKeyManagementService(cacheFactory: NamedCacheFactory, val identityService: PersistentIdentityService,
                                   private val database: CordaPersistence, private val cryptoService: CryptoService) : SingletonSerializeAsToken(), KeyManagementServiceInternal {
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
                    fromPersistentEntity = {
                        Pair(Crypto.decodePublicKey(it.publicKey), Crypto.decodePrivateKey(
                                it.privateKey))
                    },
                    toPersistentEntity = { key: PublicKey, value: PrivateKey ->
                        PersistentKey(key, value)
                    },
                    persistentEntityClass = PersistentKey::class.java
            )
        }

        fun createExternalIdMap(cacheFactory: NamedCacheFactory, database: CordaPersistence): AppendOnlyPersistentMapBase<String, UUID, PublicKeyHashToExternalId, String> {
            return AppendOnlyPersistentMap(
                        cacheFactory = cacheFactory,
                        name = "BasicHSMKeyManagementService_keyToExternalId",
                        toPersistentEntityKey = { it -> it },
                        fromPersistentEntity = { it.publicKeyHash to it.externalId },
                        toPersistentEntity = { keyHash: String, uuid: UUID -> PublicKeyHashToExternalId(uuid, keyHash) },
                        persistentEntityClass = PublicKeyHashToExternalId::class.java)
        }
    }

    // Maintain a map from PublicKey to alias for the initial keys.
    private val originalKeysMap = mutableMapOf<PublicKey, String>()
    // A map for anonymous keys.
    private val keysMap = createKeyMap(cacheFactory)
    private val keyToExternalId = createExternalIdMap(cacheFactory, database)

    override fun start(initialKeyPairs: Set<KeyPair>) {
        initialKeyPairs.forEach {
            require(it.private is AliasPrivateKey) { "${this.javaClass.name} supports AliasPrivateKeys only, but ${it.private.algorithm} key was found" }
            originalKeysMap[Crypto.toSupportedPublicKey(it.public)] = (it.private as AliasPrivateKey).alias
        }
        database.transaction {
            keyToExternalId.preLoaded(limit = 100_000, orderingField = PublicKeyHashToExternalId::dateMapped, ascending = false)
        }
    }

    override val keys: Set<PublicKey> get() = database.transaction { originalKeysMap.keys.plus(keysMap.allPersisted().map { it.first }.toSet()) }

    private fun containsPublicKey(publicKey: PublicKey): Boolean {
        return (publicKey in originalKeysMap || publicKey in keysMap)
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> = database.transaction {
        identityService.stripNotOurKeys(candidateKeys)
    }

    // Unlike initial keys, freshkey() is related confidential keys and it utilises platform's software key generation
    // thus, without using [cryptoService]).
    override fun freshKey(): PublicKey {
        val keyPair = generateKeyPair()
        database.transaction {
            keysMap[keyPair.public] = keyPair.private
        }
        return keyPair.public
    }

    override fun freshKey(externalId: UUID): PublicKey {
        val newKey = freshKey()
        database.transaction {
            keyToExternalId.set(newKey.toStringShort(), externalId)
        }
        return newKey
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey))
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean, externalId: UUID): PartyAndCertificate {
        val newKeyWithCert = freshKeyAndCert(identity, revocationEnabled)
        database.transaction {
            keyToExternalId.set(newKeyWithCert.owningKey.toStringShort(), externalId)
        }
        return newKeyWithCert
    }

    override fun externalIdForPublicKey(publicKey: PublicKey): UUID? {
        return keyToExternalId[publicKey.toStringShort()]
    }

    private fun getSigner(publicKey: PublicKey): ContentSigner {
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

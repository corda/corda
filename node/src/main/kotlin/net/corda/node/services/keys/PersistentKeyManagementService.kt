package net.corda.node.services.keys

import net.corda.core.crypto.*
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.ThreadBox
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.utilities.*
import org.bouncycastle.operator.ContentSigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import javax.persistence.*

/**
 * A persistent re-implementation of [E2ETestKeyManagementService] to support node re-start.
 *
 * This is not the long-term implementation.  See the list of items in the above class.
 *
 * This class needs database transactions to be in-flight during method calls and init.
 */
class PersistentKeyManagementService(val identityService: IdentityService,
                                     initialKeys: Set<KeyPair>) : SingletonSerializeAsToken(), KeyManagementService {

    object PersistentKeyManagementSchema

    object PersistentKeyManagementSchemaV1 : MappedSchema(schemaFamily = PersistentKeyManagementSchema.javaClass, version = 1,
            mappedTypes = listOf(PersistentKey::class.java)) {

        @Entity
        @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}our_key_pairs")
        class PersistentKey(
                @Id
                @GeneratedValue(strategy = GenerationType.AUTO)
                var id: Int = 0,

                @Column(name = "public_key")
                var publicKey: String = "",

                @Lob
                @Column(name = "private_key")
                var privateKey: ByteArray = ByteArray(0)
        )
    }

    private companion object {
        fun createKeyMap(): AppendOnlyPersistentMap<PublicKey, PrivateKey, PersistentKeyManagementSchemaV1.PersistentKey, String> {
            return AppendOnlyPersistentMap(
                    cacheBound = 1024,
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = { Pair(parsePublicKeyBase58(it.publicKey),
                            deserializeFromByteArray<PrivateKey>(it.privateKey, context = SerializationDefaults.STORAGE_CONTEXT)) },
                    toPersistentEntity = { key: PublicKey, value: PrivateKey ->
                        PersistentKeyManagementSchemaV1.PersistentKey().apply {
                            publicKey = key.toBase58String()
                            privateKey = serializeToByteArray(value, context = SerializationDefaults.STORAGE_CONTEXT)
                        }
                    },
                    persistentEntityClass = PersistentKeyManagementSchemaV1.PersistentKey::class.java
            )
        }
    }

    val keysMap = createKeyMap()

    init {
        initialKeys.forEach({it-> keysMap[it.public] = it.private})
    }

    override val keys: Set<PublicKey> get() = keysMap.allPersisted().map { it.first }.toSet()

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> =
            candidateKeys.filter { keysMap[it] != null }

    override fun freshKey(): PublicKey {
        val keyPair = generateKeyPair()
        keysMap[keyPair.public] = keyPair.private
        return keyPair.public
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): AnonymousPartyAndPath =
            freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey), revocationEnabled)

    private fun getSigner(publicKey: PublicKey): ContentSigner  = getSigner(getSigningKeyPair(publicKey))

    private fun getSigningKeyPair(publicKey: PublicKey): KeyPair {
        val pk = publicKey.keys.first { keysMap[it] != null }
        return KeyPair(pk, keysMap[pk]!!)
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

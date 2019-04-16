package net.corda.node.services.keys

import net.corda.core.crypto.toStringShort
import net.corda.core.node.services.KeyManagementService
import org.hibernate.annotations.Type
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import javax.persistence.*

interface KeyManagementServiceInternal : KeyManagementService {
    fun start(initialKeyPairs: Set<KeyPair>)
    fun externalIdForPublicKey(publicKey: PublicKey): UUID?
}

@Entity
@Table(name = "pk_hash_to_ext_id_map", indexes = [Index(name = "pk_hash_to_xid_idx", columnList = "public_key_hash")])
class PublicKeyHashToExternalId(
        @Column(name = "external_id", nullable = false)
        @Type(type = "uuid-char")
        val externalId: UUID?,

        @Id
        @Column(name = "public_key_hash", nullable = false)
        val publicKeyHash: String

) {
    constructor(accountId: UUID, publicKey: PublicKey)
            : this(accountId, publicKey.toStringShort())

    companion object {
        fun buildEntityKey(publicKeyHash: String, externalId: UUID): String {
            return externalId.toString() + publicKeyHash
        }
    }
}
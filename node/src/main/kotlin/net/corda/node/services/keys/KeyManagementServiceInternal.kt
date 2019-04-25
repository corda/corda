package net.corda.node.services.keys

import net.corda.core.crypto.toStringShort
import net.corda.core.node.services.KeyManagementService
import org.hibernate.annotations.Type
import java.security.KeyPair
import java.security.PublicKey
import java.time.Instant
import java.util.*
import javax.persistence.*

interface KeyManagementServiceInternal : KeyManagementService {
    fun start(initialKeyPairs: Set<KeyPair>)
    fun externalIdForPublicKey(publicKey: PublicKey): UUID?
}

@Entity
@Table(name = "pk_hash_to_ext_id_map", indexes = [Index(name = "date_idx", columnList = "date_mapped")])
class PublicKeyHashToExternalId(

        @Column(name = "date_mapped", nullable = false)
        val dateMapped: Instant,

        @Column(name = "external_id", nullable = false)
        @Type(type = "uuid-char")
        val externalId: UUID,

        @Id
        @Column(name = "public_key_hash", nullable = false)
        val publicKeyHash: String

) {
    constructor(accountId: UUID, publicKey: PublicKey)
            : this(Instant.now(), accountId, publicKey.toStringShort())

    constructor(accountId: UUID, publicKeyHash: String)
            : this(Instant.now(), accountId, publicKeyHash)
}